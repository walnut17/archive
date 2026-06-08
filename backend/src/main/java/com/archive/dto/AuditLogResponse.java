package com.archive.dto;

import com.archive.entity.AuditLog;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 审计日志响应 DTO.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {

    private Long id;
    private String actor;
    private String action;
    private String entityType;
    private Long entityId;
    private String oldValue;
    private String newValue;
    private String ipAddress;
    private String userAgent;
    private String requestId;
    private String extra;
    private LocalDateTime createdAt;

    public static AuditLogResponse from(AuditLog al) {
        return AuditLogResponse.builder()
                .id(al.getId())
                .actor(al.getActor())
                .action(al.getAction())
                .entityType(al.getEntityType())
                .entityId(al.getEntityId())
                .oldValue(al.getOldValue())
                .newValue(al.getNewValue())
                .ipAddress(al.getIpAddress())
                .userAgent(al.getUserAgent())
                .requestId(al.getRequestId())
                .extra(al.getExtra())
                .createdAt(al.getCreatedAt())
                .build();
    }
}
