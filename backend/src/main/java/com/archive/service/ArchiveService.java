package com.archive.service;

import com.archive.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据生命周期归档服务 — 1 年 / 5 年扫描.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final MaterialRepository materialRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 每月 1 号凌晨 3 点执行长期归档扫描.
     */
    @Scheduled(cron = "0 0 3 1 * *")
    @Transactional
    public void scanLongTerm() {
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        LocalDateTime fiveYearsAgo = LocalDateTime.now().minusYears(5);

        List<Map<String, Object>> yearOld = jdbcTemplate.queryForList(
                """
                SELECT id FROM material
                WHERE deleted_at IS NOT NULL AND deleted_at < ? AND status = 'purged'
                """,
                oneYearAgo
        );
        for (Map<String, Object> row : yearOld) {
            Long id = ((Number) row.get("id")).longValue();
            materialRepository.findById(id).ifPresent(m -> {
                m.setStatus("archived");
                m.setArchivedAt(LocalDateTime.now());
                materialRepository.save(m);
            });
        }

        List<Map<String, Object>> fiveOld = jdbcTemplate.queryForList(
                """
                SELECT id FROM material
                WHERE archived_at IS NOT NULL AND archived_at < ? AND status = 'archived'
                """,
                fiveYearsAgo
        );
        for (Map<String, Object> row : fiveOld) {
            Long id = ((Number) row.get("id")).longValue();
            materialRepository.findById(id).ifPresent(m -> {
                m.setStatus("long_archived");
                materialRepository.save(m);
            });
        }

        log.info("Archive scan completed: archived={}, long_archived={}", yearOld.size(), fiveOld.size());
    }
}
