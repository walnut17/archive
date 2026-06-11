package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 审计日志实体 — 操作审计记录.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_al_actor_created", columnList = "actor, created_at"),
        @Index(name = "idx_al_action_created", columnList = "action, created_at"),
        @Index(name = "idx_al_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_al_request", columnList = "request_id"),
        @Index(name = "idx_al_created", columnList = "created_at")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor", nullable = false, length = 64)
    private String actor;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    /** WRITE / LOGIN / SENSITIVE_VIEW / EXPORT / LLM */
    @Column(name = "type", length = 32)
    private String type;

    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Column(name = "entity_subtype", length = 32)
    private String entitySubtype;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "old_value", columnDefinition = "JSON")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "JSON")
    private String newValue;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "extra", columnDefinition = "JSON")
    private String extra;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
