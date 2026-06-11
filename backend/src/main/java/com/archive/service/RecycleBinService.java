package com.archive.service;

import com.archive.entity.Material;
import com.archive.entity.Project;
import com.archive.entity.Proposal;
import com.archive.repository.MaterialRepository;
import com.archive.repository.ProjectRepository;
import com.archive.repository.ProposalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 回收站服务 — 软删恢复与 30 天过期扫描.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleBinService {

    private final ProjectRepository projectRepository;
    private final ProposalRepository proposalRepository;
    private final MaterialRepository materialRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void softDeleteProject(Long projectId, Long userId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("项目不存在: id=" + projectId));
        p.setStatus("deleted");
        p.setDeletedAt(LocalDateTime.now());
        p.setDeletedBy(userId);
        projectRepository.save(p);
    }

    @Transactional
    public void softDeleteProposal(Long proposalId, Long userId) {
        Proposal p = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new NoSuchElementException("议案不存在: id=" + proposalId));
        p.setStatus("deleted");
        p.setDeletedAt(LocalDateTime.now());
        p.setDeletedBy(userId);
        proposalRepository.save(p);
    }

    @Transactional
    public void softDeleteMaterial(Long materialId, Long userId) {
        Material m = materialRepository.findById(materialId)
                .orElseThrow(() -> new NoSuchElementException("材料不存在: id=" + materialId));
        m.setStatus("deleted");
        m.setDeletedAt(LocalDateTime.now());
        m.setDeletedBy(userId);
        materialRepository.save(m);
    }

    @Transactional
    public void restore(String entityType, Long entityId) {
        switch (entityType.toLowerCase()) {
            case "project" -> restoreProject(entityId);
            case "proposal" -> restoreProposal(entityId);
            case "material" -> restoreMaterial(entityId);
            case "business_term" -> restoreBusinessTerm(entityId);
            default -> throw new IllegalArgumentException("不支持的实体类型: " + entityType);
        }
    }

    public List<Map<String, Object>> listDeleted(String entityType, int limit) {
        String table = switch (entityType.toLowerCase()) {
            case "project" -> "project";
            case "proposal" -> "proposal";
            case "material" -> "material";
            case "business_term" -> "business_term";
            default -> throw new IllegalArgumentException("不支持的实体类型: " + entityType);
        };
        return jdbcTemplate.queryForList(
                """
                SELECT id, status, deleted_at, deleted_by
                FROM %s
                WHERE deleted_at IS NOT NULL
                ORDER BY deleted_at DESC
                LIMIT ?
                """.formatted(table),
                Math.max(1, Math.min(limit, 200))
        );
    }

    /**
     * 每天凌晨 2 点扫描超过 30 天的软删项目,标记为 purged.
     */
    @Scheduled(cron = "0 2 * * *")
    @Transactional
    public void scanExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<Long> expiredIds = jdbcTemplate.queryForList(
                """
                SELECT id FROM project
                WHERE status = 'deleted' AND deleted_at IS NOT NULL AND deleted_at < ?
                """,
                Long.class,
                cutoff
        );
        for (Long id : expiredIds) {
            log.info("Purging expired soft-deleted project id={}", id);
            jdbcTemplate.update("UPDATE project SET status = 'purged' WHERE id = ?", id);
        }
    }

    private void restoreProject(Long id) {
        jdbcTemplate.update(
                """
                UPDATE project SET status = '草稿', deleted_at = NULL, deleted_by = NULL
                WHERE id = ? AND deleted_at IS NOT NULL
                """,
                id
        );
    }

    private void restoreProposal(Long id) {
        jdbcTemplate.update(
                """
                UPDATE proposal SET status = '草稿', deleted_at = NULL, deleted_by = NULL
                WHERE id = ? AND deleted_at IS NOT NULL
                """,
                id
        );
    }

    private void restoreMaterial(Long id) {
        jdbcTemplate.update(
                """
                UPDATE material SET status = '草稿', deleted_at = NULL, deleted_by = NULL
                WHERE id = ? AND deleted_at IS NOT NULL
                """,
                id
        );
    }

    private void restoreBusinessTerm(Long id) {
        jdbcTemplate.update(
                """
                UPDATE business_term SET status = 'draft', deleted_at = NULL, deleted_by = NULL
                WHERE id = ? AND deleted_at IS NOT NULL
                """,
                id
        );
    }
}
