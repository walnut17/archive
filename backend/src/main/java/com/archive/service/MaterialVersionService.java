package com.archive.service;

import com.archive.common.StorageService;
import com.archive.engine.ExtractionEngine;
import com.archive.engine.TimepointExtractor;
import com.archive.engine.TriggerEngine;
import com.archive.entity.Material;
import com.archive.entity.MaterialVersion;
import com.archive.entity.Proposal;
import com.archive.repository.MaterialRepository;
import com.archive.repository.MaterialVersionRepository;
import com.archive.repository.ProposalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * 材料版本服务.
 *
 * 业务逻辑:
 *  1. 上传新版本 → 算 SHA-256 → 落盘 → Tika 解析 → 保存
 *  2. 切换"当前生效版本" → material.current_version_id 更新
 *  3. 删除版本 → 物理文件 + 数据库记录
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialVersionService {

    private final MaterialRepository materialRepository;
    private final MaterialVersionRepository materialVersionRepository;
    private final StorageService storageService;
    private final TikaService tikaService;
    private final ProposalRepository proposalRepository;

    @Autowired
    private ExtractionEngine extractionEngine;
    @Autowired
    private TimepointExtractor timepointExtractor;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private ProjectService projectService;

    /**
     * 上传新版本(自动算 version_no:同 material 已有最大 + 1).
     *
     * @param materialId  所属材料 ID
     * @param file        上传的文件
     * @param changeNote  版本说明
     * @param uploadedBy  上传人(username)
     * @return 新创建的版本
     */
    @Transactional
    public MaterialVersion upload(Long materialId, MultipartFile file, String changeNote, String uploadedBy) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new NoSuchElementException("材料不存在: id=" + materialId));
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        // 算下一个版本号
        int nextVersionNo = materialVersionRepository
                .findFirstByMaterialIdOrderByVersionNoDesc(materialId)
                .map(v -> v.getVersionNo() + 1)
                .orElse(1);

        // 计算 SHA-256
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("读上传文件失败", e);
        }
        String sha256 = sha256Hex(bytes);

        // 检查同一材料下是否已存在相同 SHA-256 的文件(去重)
        Optional<MaterialVersion> existing = materialVersionRepository
                .findByMaterialIdAndSha256(materialId, sha256);
        if (existing.isPresent()) {
            MaterialVersion dup = existing.get();
            throw new IllegalArgumentException("文件已存在,版本 v" + dup.getVersionNo());
        }

        String mimeType = tikaService.detectMimeType(bytes, file.getOriginalFilename());

        // 存盘(路径: project/{projectId}/proposal/{proposalId}/material/{materialId}/v{n}/{filename})
        String relPath = String.format("material-%d/v%d/%s",
                materialId, nextVersionNo, file.getOriginalFilename());
        try {
            storageService.saveFile(relPath, file.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("保存文件失败", e);
        }

        // 创建 version 记录
        MaterialVersion v = MaterialVersion.builder()
                .materialId(materialId)
                .versionNo(nextVersionNo)
                .originalFilename(file.getOriginalFilename())
                .storagePath(relPath)
                .fileSize(file.getSize())
                .mimeType(mimeType)
                .sha256(sha256)
                .parseStatus("pending")
                .uploadedBy(uploadedBy)
                .changeNote(changeNote)
                .build();
        MaterialVersion saved = materialVersionRepository.save(v);

        // 同步触发解析(同步,简单起见。后续可改为异步线程池)
        parseVersion(saved.getId());

        // 自动设为当前版本
        material.setCurrentVersionId(saved.getId());
        materialRepository.save(material);

        return saved;
    }

    /**
     * 触发 Tika 解析.
     */
    @Transactional
    public void parseVersion(Long versionId) {
        MaterialVersion v = materialVersionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("版本不存在: id=" + versionId));
        v.setParseStatus("running");
        materialVersionRepository.save(v);

        try {
            byte[] bytes = storageService.readFileBytes(v.getStoragePath());
            String text = tikaService.extractText(bytes);
            // 保存解析文本
            String parsedPath = v.getStoragePath() + ".txt";
            storageService.saveParsedText(parsedPath, text);
            v.setParsedTextPath(parsedPath);
            // M2 知识库问答用 — 把解析文本也存到 DB 字段,FULLTEXT 索引走这个
            v.setParsedText(text);
            v.setParseStatus("success");
            v.setParsedAt(LocalDateTime.now());
            v.setParseError(null);

            // 非阻塞:解析成功后触发引擎处理
            try {
                triggerAfterParse(v.getId(), v.getMaterialId());
            } catch (Exception e) {
                log.warn("triggerAfterParse failed (non-blocking) for version {}: {}", versionId, e.getMessage());
            }
        } catch (Exception e) {
            log.error("Parse failed for version {}", versionId, e);
            v.setParseStatus("failed");
            v.setParseError(e.getMessage());
            v.setParsedAt(LocalDateTime.now());
        }
        materialVersionRepository.save(v);
    }

    /**
     * 切换当前版本.
     */
    @Transactional
    public Material switchCurrentVersion(Long materialId, Long versionId) {
        Material m = materialRepository.findById(materialId)
                .orElseThrow(() -> new NoSuchElementException("材料不存在: id=" + materialId));
        MaterialVersion v = materialVersionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("版本不存在: id=" + versionId));
        if (!v.getMaterialId().equals(materialId)) {
            throw new IllegalArgumentException("版本不属于该材料");
        }
        m.setCurrentVersionId(versionId);
        return materialRepository.save(m);
    }

    /**
     * 删除版本(物理删除).
     */
    @Transactional
    public void deleteVersion(Long versionId) {
        MaterialVersion v = materialVersionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("版本不存在: id=" + versionId));
        Material m = materialRepository.findById(v.getMaterialId()).orElse(null);

        // 如果删除的是当前版本,清空 currentVersionId
        if (m != null && versionId.equals(m.getCurrentVersionId())) {
            // 选剩余最大版本号作为新的当前版本
            Optional<MaterialVersion> next = materialVersionRepository
                    .findFirstByMaterialIdOrderByVersionNoDesc(v.getMaterialId());
            next = next.filter(nv -> !nv.getId().equals(versionId));
            m.setCurrentVersionId(next.map(MaterialVersion::getId).orElse(null));
            materialRepository.save(m);
        }

        // 删物理文件
        storageService.deleteFile(v.getStoragePath());
        if (v.getParsedTextPath() != null) {
            // 解析文本路径不在 file-root 下,这里简化处理
            // 实际需要 StorageService.deleteParsedText() 方法
        }
        materialVersionRepository.delete(v);
    }

    public List<MaterialVersion> listByMaterialId(Long materialId) {
        return materialVersionRepository.findByMaterialIdOrderByVersionNoDesc(materialId);
    }

    public MaterialVersion getById(Long id) {
        return materialVersionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("版本不存在: id=" + id));
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    /**
     * 解析成功后触发引擎处理(非阻塞).
     * <p>调用抽取引擎、时点抽取引擎，发布分类事件，
     * 若材料类别为收款/付款凭证则更新项目剩余金额。</p>
     *
     * @param materialVersionId 材料版本 ID
     * @param materialId        材料 ID
     */
    @Async("taskExecutor")
    public void triggerAfterParse(Long materialVersionId, Long materialId) {
        try {
            MaterialVersion v = materialVersionRepository.findById(materialVersionId).orElse(null);
            Material material = materialRepository.findById(materialId).orElse(null);
            if (v == null || material == null) {
                log.warn("triggerAfterParse skipped: version={} or material={} not found",
                        materialVersionId, materialId);
                return;
            }

            // 1. 字段抽取引擎
            try {
                extractionEngine.extract(materialVersionId);
            } catch (Exception e) {
                log.warn("Extraction engine call failed for version {}: {}", materialVersionId, e.getMessage());
            }

            // 2. 时点抽取引擎
            try {
                timepointExtractor.extractTimepoints(materialVersionId);
            } catch (Exception e) {
                log.warn("Timepoint extraction failed for version {}: {}", materialVersionId, e.getMessage());
            }

            // 3. 发布材料分类事件(触发规则引擎)
            try {
                TriggerEngine.MaterialCategorizedEvent event = new TriggerEngine.MaterialCategorizedEvent(
                        materialId, material.getTitle(), material.getCategory());
                eventPublisher.publishEvent(event);
            } catch (Exception e) {
                log.warn("Failed to publish MaterialCategorizedEvent for material {}: {}", materialId, e.getMessage());
            }

            // 4. 若为收款/付款凭证, 更新项目剩余金额
            String category = material.getCategory();
            if ("收款凭证".equals(category) || "付款凭证".equals(category)) {
                try {
                    Proposal proposal = proposalRepository.findById(material.getProposalId()).orElse(null);
                    if (proposal != null) {
                        projectService.updateRemainingAmount(proposal.getProjectId());
                    }
                } catch (Exception e) {
                    log.warn("Failed to updateRemainingAmount for proposal {}: {}",
                            material.getProposalId(), e.getMessage());
                }
            }

            log.info("triggerAfterParse completed for version={}, material={}", materialVersionId, materialId);
        } catch (Exception e) {
            log.warn("triggerAfterParse failed for version {}: {}", materialVersionId, e.getMessage());
        }
    }
}
