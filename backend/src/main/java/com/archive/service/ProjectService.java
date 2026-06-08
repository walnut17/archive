package com.archive.service;

import com.archive.dto.PageResponse;
import com.archive.dto.ProjectRequest;
import com.archive.entity.Project;
import com.archive.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** 合法状态流转. */
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
        return projectRepository.save(p);
    }

    @Transactional
    public Project update(Long id, ProjectRequest req) {
        Project p = projectRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("项目不存在: id=" + id));

        // 编号不可改(业务上项目编号是稳定标识)
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
        if (req.getStatus() != null) p.setStatus(req.getStatus());
        p.setScheduledMeetingAt(req.getScheduledMeetingAt());
        p.setRemark(req.getRemark());
        return projectRepository.save(p);
    }

    @Transactional
    public void delete(Long id) {
        Project p = projectRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("项目不存在: id=" + id));
        if (!"草稿".equals(p.getStatus()) && !"撤回".equals(p.getStatus())) {
            throw new IllegalStateException("只有草稿或撤回状态可删除");
        }
        projectRepository.delete(p);
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

    /**
     * 更新项目剩余金额(占位实现).
     * <p>完整实现需聚合付款材料数据计算: remaining = initial_amount - sum(payments)。
     * 当前仅将 remaining_amount 设为 initial_amount。
     * DB 已有 initial_amount / remaining_amount 列，Java 实体尚未映射。</p>
     *
     * @param projectId 项目 ID
     */
    @Transactional
    public void updateRemainingAmount(Long projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            log.warn("updateRemainingAmount skipped: project not found, id={}", projectId);
            return;
        }
        // 占位:实体尚未映射 initial_amount / remaining_amount 字段,
        // 待 DB 实体同步后实现 actual = initial - sum(已付款)
        log.info("updateRemainingAmount called for projectId={} (placeholder)", projectId);
    }
}
