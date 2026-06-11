package com.archive.service;

import com.archive.entity.Proposal;
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
 * 议案编号生成器 — v1.1 预留/释放/改系列.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalNumberGenerator {

    private final JdbcTemplate jdbcTemplate;
    private final ProposalRepository proposalRepository;

    /**
     * v1.0 兼容: 直接生成编号(不预留).
     */
    @Transactional
    public String legacyGenerate(String seriesCode, Long projectId) {
        Proposal p = reserve(seriesCode, projectId);
        p.setStatus("草稿");
        p.setReservedAt(null);
        proposalRepository.save(p);
        return p.getCode();
    }

    /**
     * 预留编号 24h 有效.
     */
    @Transactional
    public Proposal reserve(String seriesCode, Long projectId) {
        Map<String, Object> series = jdbcTemplate.queryForMap(
                """
                SELECT id, prefix, current_seq FROM proposal_series
                WHERE code = ? FOR UPDATE
                """,
                seriesCode
        );
        int nextSeq = ((Number) series.get("current_seq")).intValue() + 1;
        String prefix = series.get("prefix") != null ? series.get("prefix").toString() : seriesCode + "-";
        String code = prefix + String.format("%03d", nextSeq);

        jdbcTemplate.update(
                "UPDATE proposal_series SET current_seq = ?, updated_at = NOW() WHERE id = ?",
                nextSeq,
                series.get("id")
        );

        Proposal p = Proposal.builder()
                .code(code)
                .title("预留议案-" + code)
                .projectId(projectId)
                .status("DRAFT_RESERVED")
                .reservedAt(LocalDateTime.now())
                .build();
        return proposalRepository.save(p);
    }

    /**
     * 释放预留编号 — 加 .revoked 后缀保持 UNIQUE.
     */
    @Transactional
    public void release(Long proposalId) {
        Proposal p = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new NoSuchElementException("议案不存在: id=" + proposalId));
        if (!p.getCode().endsWith(".revoked")) {
            p.setCode(p.getCode() + ".revoked");
        }
        p.setStatus("REVOKED");
        p.setReleasedAt(LocalDateTime.now());
        proposalRepository.save(p);
    }

    /**
     * 更改议案系列(仅 DRAFT / DRAFT_RESERVED 状态).
     */
    @Transactional
    public Proposal changeSeries(Long proposalId, String newSeriesCode) {
        Proposal p = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new NoSuchElementException("议案不存在: id=" + proposalId));
        if ("审议中".equals(p.getStatus()) || "通过".equals(p.getStatus())
                || "OPEN".equalsIgnoreCase(p.getStatus()) || "CLOSED".equalsIgnoreCase(p.getStatus())) {
            throw new IllegalStateException("已开投委会，不可改系列");
        }
        release(proposalId);
        return reserve(newSeriesCode, p.getProjectId());
    }

    /**
     * 每小时扫描 24h 未确认的预留编号并释放.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void releaseExpiredReservations() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<Long> expiredIds = jdbcTemplate.queryForList(
                """
                SELECT id FROM proposal
                WHERE status = 'DRAFT_RESERVED' AND reserved_at IS NOT NULL AND reserved_at < ?
                """,
                Long.class,
                cutoff
        );
        for (Long id : expiredIds) {
            log.info("Auto-releasing expired proposal reservation id={}", id);
            release(id);
        }
    }
}
