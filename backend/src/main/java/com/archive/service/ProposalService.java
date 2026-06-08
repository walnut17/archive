package com.archive.service;

import com.archive.dto.PageResponse;
import com.archive.dto.ProposalRequest;
import com.archive.entity.Project;
import com.archive.entity.Proposal;
import com.archive.repository.ProjectRepository;
import com.archive.repository.ProposalRepository;
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
        p.setTitle(req.getTitle());
        p.setType(req.getType());
        p.setSummary(req.getSummary());
        if (req.getStatus() != null) p.setStatus(req.getStatus());
        p.setReviewedAt(req.getReviewedAt());
        p.setDecision(req.getDecision());
        p.setRemark(req.getRemark());
        return proposalRepository.save(p);
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
}
