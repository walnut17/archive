package com.archive.service;

import com.archive.dto.PageResponse;
import com.archive.dto.ProposalRequest;
import com.archive.engine.ComparisonEngine;
import com.archive.entity.Material;
import com.archive.entity.MaterialVersion;
import com.archive.entity.Project;
import com.archive.entity.Proposal;
import com.archive.provider.LLMProvider;
import com.archive.provider.LLMProviderFactory;
import com.archive.repository.MaterialRepository;
import com.archive.repository.MaterialVersionRepository;
import com.archive.repository.ProjectRepository;
import com.archive.repository.ProposalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 议案业务逻辑.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalService {

    private final ProposalRepository proposalRepository;
    private final ProjectRepository projectRepository;

    @Autowired
    private ComparisonEngine comparisonEngine;
    @Autowired
    private MaterialRepository materialRepository;
    @Autowired
    private MaterialVersionRepository materialVersionRepository;
    @Autowired
    private LLMProviderFactory llmProviderFactory;

    private static final java.util.Set<String> VALID_STATUSES = java.util.Set.of(
            "草稿", "已提交", "审议中", "通过", "暂缓", "否决", "撤回"
    );

    @Transactional
    public Proposal create(ProposalRequest req) {
        if (proposalRepository.existsByCode(req.getCode())) {
            throw new IllegalArgumentException("议案编号已存在: " + req.getCode());
        }
        // 校验项目存在
        if (!projectRepository.existsById(req.getProjectId())) {
            throw new NoSuchElementException("项目不存在: id=" + req.getProjectId());
        }
        if (req.getStatus() != null && !VALID_STATUSES.contains(req.getStatus())) {
            throw new IllegalArgumentException("非法状态: " + req.getStatus());
        }
        Proposal p = Proposal.builder()
                .code(req.getCode())
                .title(req.getTitle())
                .projectId(req.getProjectId())
                .type(req.getType())
                .summary(req.getSummary())
                .status(req.getStatus() != null ? req.getStatus() : "草稿")
                .reviewedAt(req.getReviewedAt())
                .decision(req.getDecision())
                .remark(req.getRemark())
                .build();
        return proposalRepository.save(p);
    }

    @Transactional
    public Proposal update(Long id, ProposalRequest req) {
        Proposal p = proposalRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("议案不存在: id=" + id));
        if (!p.getCode().equals(req.getCode())) {
            throw new IllegalArgumentException("议案编号不可修改");
        }
        // 如果改了 projectId,校验新项目存在
        if (!p.getProjectId().equals(req.getProjectId())) {
            if (!projectRepository.existsById(req.getProjectId())) {
                throw new NoSuchElementException("项目不存在: id=" + req.getProjectId());
            }
            p.setProjectId(req.getProjectId());
        }
        if (req.getStatus() != null && !VALID_STATUSES.contains(req.getStatus())) {
            throw new IllegalArgumentException("非法状态: " + req.getStatus());
        }
        // 记录状态变化,用于触发后续流程
        String oldStatus = p.getStatus();
        p.setTitle(req.getTitle());
        p.setType(req.getType());
        p.setSummary(req.getSummary());
        if (req.getStatus() != null) p.setStatus(req.getStatus());
        p.setReviewedAt(req.getReviewedAt());
        p.setDecision(req.getDecision());
        p.setRemark(req.getRemark());
        Proposal saved = proposalRepository.save(p);

        // 非阻塞:状态变为"已提交"时触发对比引擎 + 自动摘要
        if (!"已提交".equals(oldStatus) && "已提交".equals(saved.getStatus())) {
            triggerComparison(saved.getId());
            if (saved.getSummary() == null || saved.getSummary().isBlank()) {
                triggerAutoSummary(saved.getId());
            }
        }

        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Proposal p = proposalRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("议案不存在: id=" + id));
        if (!"草稿".equals(p.getStatus()) && !"撤回".equals(p.getStatus())) {
            throw new IllegalStateException("只有草稿或撤回状态可删除");
        }
        proposalRepository.delete(p);
    }

    public Proposal getById(Long id) {
        return proposalRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("议案不存在: id=" + id));
    }

    public PageResponse<Proposal> list(int page, int size, Long projectId, String status, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Proposal> result;
        if (keyword != null && !keyword.isBlank()) {
            result = proposalRepository.searchByKeyword(keyword.trim(), pageable);
        } else if (projectId != null) {
            result = proposalRepository.findByProjectId(projectId, pageable);
        } else if (status != null && !status.isBlank()) {
            result = proposalRepository.findByStatus(status, pageable);
        } else {
            result = proposalRepository.findAll(pageable);
        }
        return PageResponse.of(result);
    }

    /**
     * 议案提交后触发对比引擎(非阻塞).
     * <p>查找议案的"立项"和"申请"类型材料的最新版本，调用对比引擎生成差异分析。</p>
     *
     * @param proposalId 议案 ID
     */
    @Async("taskExecutor")
    public void triggerComparison(Long proposalId) {
        try {
            Proposal proposal = proposalRepository.findById(proposalId).orElse(null);
            if (proposal == null) {
                log.warn("triggerComparison skipped: proposal not found, id={}", proposalId);
                return;
            }

            // 查找"立项"类型材料
            Material fromMaterial = materialRepository.findByProposalId(proposalId).stream()
                    .filter(m -> "立项".equals(m.getCategory()))
                    .findFirst().orElse(null);

            // 查找"申请"类型材料
            Material toMaterial = materialRepository.findByProposalId(proposalId).stream()
                    .filter(m -> "申请".equals(m.getCategory()))
                    .findFirst().orElse(null);

            if (fromMaterial == null || toMaterial == null) {
                log.info("triggerComparison skipped for proposal {}: missing 立项/申请 material", proposalId);
                return;
            }

            // 获取各自最新版本
            MaterialVersion fromVersion = materialVersionRepository
                    .findFirstByMaterialIdOrderByVersionNoDesc(fromMaterial.getId())
                    .orElse(null);
            MaterialVersion toVersion = materialVersionRepository
                    .findFirstByMaterialIdOrderByVersionNoDesc(toMaterial.getId())
                    .orElse(null);

            if (fromVersion == null || toVersion == null) {
                log.info("triggerComparison skipped for proposal {}: missing version for 立项/申请", proposalId);
                return;
            }

            // 调用对比引擎
            var result = comparisonEngine.compare(
                    proposal.getProjectId(),
                    fromVersion.getId(),
                    toVersion.getId(),
                    "DEFAULT_QA_VERIFY");
            log.info("triggerComparison completed for proposal {}: {} items", proposalId, result.size());
        } catch (Exception e) {
            log.warn("triggerComparison failed for proposal {}: {}", proposalId, e.getMessage());
        }
    }

    /**
     * 议案提交后自动生成摘要(非阻塞).
     * <p>优先从"立项报告 / 申请报告"解析文本中提取摘要，写入 proposal.summary。</p>
     *
     * @param proposalId 议案 ID
     */
    @Async("taskExecutor")
    public void triggerAutoSummary(Long proposalId) {
        try {
            Proposal proposal = proposalRepository.findById(proposalId).orElse(null);
            if (proposal == null) return;
            if (proposal.getSummary() != null && !proposal.getSummary().isBlank()) return;

            String summary = generateSummaryFromMaterials(proposalId);
            if (summary == null) {
                log.info("triggerAutoSummary skipped for proposal {}: no parsed material content", proposalId);
                return;
            }
            saveSummary(proposal, summary);
            log.info("triggerAutoSummary completed for proposal {}", proposalId);
        } catch (Exception e) {
            log.warn("triggerAutoSummary failed for proposal {}: {}", proposalId, e.getMessage());
        }
    }

    /**
     * 重新生成摘要(同步).
     * <p>清空现有摘要并从材料解析文本中重新生成。</p>
     *
     * @param proposalId 议案 ID
     * @return 更新后的议案
     */
    @Transactional
    public Proposal regenerateSummary(Long proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new NoSuchElementException("议案不存在: id=" + proposalId));
        proposal.setSummary(null);
        String summary = generateSummaryFromMaterials(proposalId);
        if (summary != null) {
            saveSummary(proposal, summary);
        }
        return proposalRepository.save(proposal);
    }

    /**
     * 从材料解析文本中生成摘要.
     *
     * @param proposalId 议案 ID
     * @return 生成的摘要文本，若无可用内容返回 null
     */
    private String generateSummaryFromMaterials(Long proposalId) {
        // 1. 找最佳解析内容:优先 立项报告 > 申请报告 > 任一已解析
        List<Material> materials = materialRepository.findByProposalId(proposalId);
        String sourceContent = null;
        for (String priorityCat : new String[]{"立项报告", "申请报告"}) {
            for (Material m : materials) {
                if (priorityCat.equals(m.getCategory()) && m.getCurrentVersionId() != null) {
                    MaterialVersion v = materialVersionRepository.findById(m.getCurrentVersionId()).orElse(null);
                    if (v != null && "success".equals(v.getParseStatus()) && v.getParsedText() != null && !v.getParsedText().isBlank()) {
                        sourceContent = v.getParsedText();
                        break;
                    }
                }
            }
            if (sourceContent != null) break;
        }
        // 兜底:任意已解析材料
        if (sourceContent == null) {
            for (Material m : materials) {
                if (m.getCurrentVersionId() != null) {
                    MaterialVersion v = materialVersionRepository.findById(m.getCurrentVersionId()).orElse(null);
                    if (v != null && "success".equals(v.getParseStatus()) && v.getParsedText() != null && !v.getParsedText().isBlank()) {
                        sourceContent = v.getParsedText();
                        break;
                    }
                }
            }
        }
        if (sourceContent == null) return null;

        // 2. 截断过长内容
        if (sourceContent.length() > 8000) {
            sourceContent = sourceContent.substring(0, 8000);
        }

        // 3. 调用 LLM
        LLMProvider provider = llmProviderFactory.getProvider();
        String systemPrompt = "你是一名投委会秘书，需要为审议议案生成简洁的摘要。";
        String userPrompt = "请从以下材料内容中提取 200-500 字摘要，涵盖：项目背景、主要风险、审议要点。\n\n材料内容：\n" + sourceContent;
        String summary = provider.chat(systemPrompt, userPrompt);

        if (summary != null && !summary.isBlank()) {
            if (summary.length() > 2000) summary = summary.substring(0, 2000);
            return summary.trim();
        }
        return null;
    }

    /**
     * 保存摘要并追加自动生成标记.
     */
    private void saveSummary(Proposal proposal, String summary) {
        proposal.setSummary(summary);
        String mark = "[摘要由系统自动生成于 " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "]";
        String existingRemark = proposal.getRemark();
        if (existingRemark == null || existingRemark.isBlank()) {
            proposal.setRemark(mark);
        } else {
            if (!existingRemark.contains("[摘要由系统自动生成")) {
                proposal.setRemark(existingRemark + "\n" + mark);
            }
        }
    }
}
