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
 * 记录系统中所有关键操作(登录/上传/更新/删除/LLM 调用/规则触发等),
 * 不关联业务实体外键,业务表删除不影响审计数据完整性。
 * request_id 通过 MDC 注入,可串联一次请求的所有日志。
 * 注意:本实体不继承 BaseEntity,因为 audit_log 表只有 created_at,
 * 没有 created_by / updated_by / updated_at 字段。
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

    /** 操作人(username). */
    @Column(name = "actor", nullable = false, length = 64)
    private String actor;

    /** 操作类型:login/upload/update/delete/recalc_amount/llm_call/rule_fire/... */
    @Column(name = "action", nullable = false, length = 64)
    private String action;

    /** 业务实体类型:project/proposal/material/todo/... */
    @Column(name = "entity_type", length = 64)
    private String entityType;

    /** 业务实体 ID. */
    @Column(name = "entity_id")
    private Long entityId;

    /** 改动前(JSON). */
    @Column(name = "old_value", columnDefinition = "JSON")
    private String oldValue;

    /** 改动后(JSON). */
    @Column(name = "new_value", columnDefinition = "JSON")
    private String newValue;

    /** 客户端 IP 地址(IPv4/IPv6). */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** User-Agent. */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** 请求唯一 ID(MDC 注入). */
    @Column(name = "request_id", length = 64)
    private String requestId;

    /** 扩展字段(JSON),如 LLM prompt 摘要、规则命中详情等. */
    @Column(name = "extra", columnDefinition = "JSON")
    private String extra;

    /** 创建时间. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
