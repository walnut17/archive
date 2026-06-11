package com.archive.service;

import com.archive.common.OptimisticLockException;
import com.archive.dto.PageResponse;
import com.archive.dto.ProjectRequest;
import com.archive.entity.Project;
import com.archive.entity.ProjectFactEvent;
import com.archive.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

/**
 * 项目业务逻辑.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final RecycleBinService recycleBinService;

    @PersistenceContext
    private EntityManager entityManager;

    private static final java.util.Set<String> VALID_STATUSES = java.util.Set.of(
            "草稿", "待审议", "审议中", "通过", "暂缓", "否决", "撤回"
    );

    @Transactional
    public Project create(ProjectRequest req) {
        if (projectRepository.existsByCode(req.getCode())) {
            throw new IllegalArgumentException("项目编号已存在: " + req.getCode());
        }
        if (req.getStatus() != null && !VALID_STATUSES.contains(req.getStatus())) {
            throw new IllegalArgumentException("非法状态: " + req.getStatus());
        }
        Project p = Project.builder()
                .code(req.getCode())
                .name(req.getName())
                .category(req.getCategory())
                .ownerId(req.getOwnerId())
                .amountWan(req.getAmountWan())
                .summary(req.getSummary())
                .status(req.getStatus() != null ? req.getStatus() : "草稿")
                .scheduledMeetingAt(req.getScheduledMeetingAt())
                .remark(req.getRemark())
                .build();
        return saveWithVersionCheck(p);
    }

    @Transactional
    public Project update(Long id, ProjectRequest req) {
        Project p = projectRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("项目不存在: id=" + id));

        if (!p.getCode().equals(req.getCode())) {
            throw new IllegalArgumentException("项目编号不可修改");
        }
        if (req.getStatus() != null && !VALID_STATUSES.contains(req.getStatus())) {
            throw new IllegalArgumentException("非法状态: " + req.getStatus());
        }

        p.setName(req.getName());
        p.setCategory(req.getCategory());
        p.setOwnerId(req.getOwnerId());
        p.setAmountWan(req.getAmountWan());
        p.setSummary(req.getSummary());
        if (req.getStatus() != null) {
            p.setStatus(req.getStatus());
        }
        p.setScheduledMeetingAt(req.getScheduledMeetingAt());
        p.setRemark(req.getRemark());
        return saveWithVersionCheck(p);
    }

    @Transactional
    public void softDelete(Long id, Long userId) {
        recycleBinService.softDeleteProject(id, userId);
    }

    @Transactional
    @Deprecated
    public void delete(Long id) {
        Project p = projectRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("项目不存在: id=" + id));
        if (!"草稿".equals(p.getStatus()) && !"撤回".equals(p.getStatus())) {
            throw new IllegalStateException("只有草稿或撤回状态可删除");
        }
        recycleBinService.softDeleteProject(id, null);
    }

    @Transactional
    public Project rollback(Long projectId, int targetVersion, Long userId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("项目不存在: id=" + projectId));
        if (targetVersion < 1 || targetVersion >= p.getVersion()) {
            throw new IllegalArgumentException("无效的目标版本: " + targetVersion);
        }

        ProjectFactEvent evt = ProjectFactEvent.builder()
                .projectId(projectId)
                .factType("ROLLBACK")
                .eventType("ROLLBACK")
                .factValue("回滚到 version=" + targetVersion)
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .build();
        entityManager.persist(evt);

        p.setVersion(p.getVersion() + 1);
        return saveWithVersionCheck(p);
    }

    public Project getById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("项目不存在: id=" + id));
    }

    public PageResponse<Project> list(int page, int size, String status, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Project> result;
        if (keyword != null && !keyword.isBlank()) {
            result = projectRepository.searchByKeyword(keyword.trim(), pageable);
        } else if (status != null && !status.isBlank()) {
            result = projectRepository.findByStatus(status, pageable);
        } else {
            result = projectRepository.findAll(pageable);
        }
        return PageResponse.of(result);
    }

    @Transactional
    public void updateRemainingAmount(Long projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            log.warn("updateRemainingAmount skipped: project not found, id={}", projectId);
            return;
        }
        log.info("updateRemainingAmount called for projectId={} (placeholder)", projectId);
    }

    private Project saveWithVersionCheck(Project p) {
        try {
            return projectRepository.save(p);
        } catch (OptimisticLockingFailureException e) {
            throw new OptimisticLockException("数据已被他人修改，请刷新后重试", e);
        }
    }
}
