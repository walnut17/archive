package com.archive.service;

import com.archive.common.OptimisticLockException;
import com.archive.dto.MaterialRequest;
import com.archive.dto.PageResponse;
import com.archive.entity.Material;
import com.archive.repository.MaterialRepository;
import com.archive.repository.MaterialVersionRepository;
import com.archive.repository.ProposalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 材料业务逻辑.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialVersionRepository materialVersionRepository;
    private final ProposalRepository proposalRepository;
    private final RecycleBinService recycleBinService;

    @Autowired
    private MaterialVersionService materialVersionService;

    private static final java.util.Set<String> VALID_STATUSES = java.util.Set.of(
            "草稿", "评审中", "已通过", "已归档", "已作废"
    );

    @Transactional
    public Material create(MaterialRequest req) {
        if (!proposalRepository.existsById(req.getProposalId())) {
            throw new NoSuchElementException("议案不存在: id=" + req.getProposalId());
        }
        if (req.getStatus() != null && !VALID_STATUSES.contains(req.getStatus())) {
            throw new IllegalArgumentException("非法状态: " + req.getStatus());
        }
        Material m = Material.builder()
                .proposalId(req.getProposalId())
                .title(req.getTitle())
                .category(req.getCategory())
                .status(req.getStatus() != null ? req.getStatus() : "草稿")
                .description(req.getDescription())
                .tags(req.getTags())
                .build();
        return saveWithVersionCheck(m);
    }

    @Transactional
    public Material update(Long id, MaterialRequest req) {
        Material m = materialRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("材料不存在: id=" + id));
        if (req.getStatus() != null && !VALID_STATUSES.contains(req.getStatus())) {
            throw new IllegalArgumentException("非法状态: " + req.getStatus());
        }
        m.setTitle(req.getTitle());
        m.setCategory(req.getCategory());
        if (req.getStatus() != null) {
            m.setStatus(req.getStatus());
        }
        m.setDescription(req.getDescription());
        m.setTags(req.getTags());
        return saveWithVersionCheck(m);
    }

    @Transactional
    public void softDelete(Long id, Long userId) {
        recycleBinService.softDeleteMaterial(id, userId);
    }

    @Transactional
    public void delete(Long id) {
        Material m = materialRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("材料不存在: id=" + id));
        long versionCount = materialVersionRepository.countByMaterialId(id);
        if (versionCount > 0) {
            throw new IllegalStateException("材料下还有 " + versionCount + " 个版本,不可删除");
        }
        recycleBinService.softDeleteMaterial(id, null);
    }

    public Material getById(Long id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("材料不存在: id=" + id));
    }

    public PageResponse<Material> list(int page, int size, Long proposalId, String category, String status, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Material> result;
        if (keyword != null && !keyword.isBlank()) {
            result = materialRepository.searchByKeyword(keyword.trim(), pageable);
        } else if (proposalId != null) {
            result = materialRepository.findByProposalId(proposalId, pageable);
        } else if (status != null && !status.isBlank()) {
            result = materialRepository.findByStatus(status, pageable);
        } else {
            result = materialRepository.findAll(pageable);
        }
        return PageResponse.of(result);
    }

    public long countVersions(Long materialId) {
        return materialVersionRepository.countByMaterialId(materialId);
    }

    @Transactional
    public List<Material> batchUpload(Long proposalId, MultipartFile[] files,
                                      String defaultCategory, String defaultTags,
                                      String uploadedBy) {
        if (!proposalRepository.existsById(proposalId)) {
            throw new NoSuchElementException("议案不存在: id=" + proposalId);
        }
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("文件列表不能为空");
        }
        if (files.length > 20) {
            throw new IllegalArgumentException("每次最多上传20个文件");
        }

        String category = (defaultCategory != null && !defaultCategory.isBlank())
                ? defaultCategory : "其他";

        List<Material> materials = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }

            String originalFilename = file.getOriginalFilename();
            String title = originalFilename;
            if (title != null && title.contains(".")) {
                title = title.substring(0, title.lastIndexOf('.'));
            }
            if (title == null || title.isBlank()) {
                title = "未命名文件";
            }

            Material m = Material.builder()
                    .proposalId(proposalId)
                    .title(title)
                    .category(category)
                    .status("草稿")
                    .tags(defaultTags)
                    .build();
            m = materialRepository.save(m);

            materialVersionService.upload(m.getId(), file, null, uploadedBy);
            materials.add(m);
        }

        return materials;
    }

    private Material saveWithVersionCheck(Material m) {
        try {
            return materialRepository.save(m);
        } catch (OptimisticLockingFailureException e) {
            throw new OptimisticLockException("数据已被他人修改，请刷新后重试", e);
        }
    }
}
