package com.archive.service;

import com.archive.dto.PageResponse;
import com.archive.entity.AuditLog;
import com.archive.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 审计日志业务逻辑.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * 记录审计日志(全字段).
     */
    @Transactional
    public AuditLog log(String actor, String action, String entityType, Long entityId,
                        String oldValue, String newValue, String ipAddress,
                        String userAgent, String requestId, String extra) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("操作类型不能为空");
        }

        AuditLog auditLog = AuditLog.builder()
                .actor(actor.trim())
                .action(action.trim())
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .requestId(requestId)
                .extra(extra)
                .build();
        return auditLogRepository.save(auditLog);
    }

    /**
     * 记录审计日志(简洁版).
     */
    @Transactional
    public AuditLog logSimple(String actor, String action, String entityType, Long entityId) {
        return log(actor, action, entityType, entityId, null, null, null, null, null, null);
    }

    /**
     * 按操作人分页查询.
     */
    public PageResponse<AuditLog> listByActor(String actor, int page, int size) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        Page<AuditLog> result = auditLogRepository.findByActorOrderByCreatedAtDesc(actor,
                PageRequest.of(page, size));
        return PageResponse.of(result);
    }

    /**
     * 按操作类型分页查询.
     */
    public PageResponse<AuditLog> listByAction(String action, int page, int size) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("操作类型不能为空");
        }
        Page<AuditLog> result = auditLogRepository.findByActionOrderByCreatedAtDesc(action,
                PageRequest.of(page, size));
        return PageResponse.of(result);
    }

    /**
     * 按业务实体分页查询.
     */
    public PageResponse<AuditLog> listByEntity(String entityType, Long entityId, int page, int size) {
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("实体类型不能为空");
        }
        if (entityId == null) {
            throw new IllegalArgumentException("实体 ID 不能为空");
        }
        Page<AuditLog> result = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                entityType, entityId, PageRequest.of(page, size));
        return PageResponse.of(result);
    }

    /**
     * 分页查询所有审计日志(按创建时间倒序).
     */
    public PageResponse<AuditLog> listAll(int page, int size) {
        Page<AuditLog> result = auditLogRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(page, size));
        return PageResponse.of(result);
    }

    /**
     * 清理指定时间之前的日志.
     *
     * @param before 清理该时间之前的日志
     * @return -1 表示已执行(deleteByCreatedAtBefore 返回 void)
     */
    @Transactional
    public int cleanOldLogs(LocalDateTime before) {
        if (before == null) {
            throw new IllegalArgumentException("清理截止时间不能为空");
        }
        auditLogRepository.deleteByCreatedAtBefore(before);
        log.info("已清理 {} 之前的审计日志", before);
        return -1;
    }
}
