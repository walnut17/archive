package com.archive.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 失败兜底日志服务.
 *
 * @author Mavis
 */
@Service
@RequiredArgsConstructor
public class FailureLogService {

    @PersistenceContext
    private EntityManager entityManager;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void log(String path, String failureType, String errorMsg, String stackTrace) {
        entityManager.createNativeQuery(
                """
                INSERT INTO failure_log (path, failure_type, error_msg, stack_trace, resolved, occurred_at)
                VALUES (:path, :failureType, :errorMsg, :stackTrace, 0, :occurredAt)
                """
        )
                .setParameter("path", truncate(path, 512))
                .setParameter("failureType", truncate(failureType, 64))
                .setParameter("errorMsg", errorMsg)
                .setParameter("stackTrace", stackTrace)
                .setParameter("occurredAt", LocalDateTime.now())
                .executeUpdate();
    }

    public List<Map<String, Object>> listUnresolved(int limit) {
        return jdbcTemplate.queryForList(
                """
                SELECT id, path, failure_type, error_msg, stack_trace, resolved, occurred_at, resolved_at
                FROM failure_log
                WHERE resolved = 0
                ORDER BY occurred_at DESC
                LIMIT ?
                """,
                Math.max(1, Math.min(limit, 200))
        );
    }

    @Transactional
    public void markResolved(Long id) {
        jdbcTemplate.update(
                "UPDATE failure_log SET resolved = 1, resolved_at = ? WHERE id = ?",
                LocalDateTime.now(),
                id
        );
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
