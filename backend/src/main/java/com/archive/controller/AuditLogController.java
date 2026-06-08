package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.dto.AuditLogResponse;
import com.archive.dto.PageResponse;
import com.archive.entity.AuditLog;
import com.archive.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 审计日志 API.
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ApiResponse<PageResponse<AuditLogResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId) {
        PageResponse<AuditLog> result;
        if (actor != null) {
            result = auditLogService.listByActor(actor, page, size);
        } else if (action != null) {
            result = auditLogService.listByAction(action, page, size);
        } else if (entityType != null && entityId != null) {
            result = auditLogService.listByEntity(entityType, entityId, page, size);
        } else {
            result = auditLogService.listAll(page, size);
        }
        return ApiResponse.ok(result.mapContent(AuditLogResponse::from));
    }

    @DeleteMapping("/clean")
    public ApiResponse<Void> clean(@RequestParam String before) {
        LocalDateTime beforeTime = LocalDateTime.parse(before);
        auditLogService.cleanOldLogs(beforeTime);
        return ApiResponse.ok();
    }
}
