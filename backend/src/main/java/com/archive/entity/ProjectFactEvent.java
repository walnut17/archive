package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 关键事实事件流 — INSERT-only (DB 触发器 + EntityListener 双保险).
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "project_fact_event", indexes = {
        @Index(name = "idx_pfe_project_type", columnList = "project_id, fact_type"),
        @Index(name = "idx_owner_due", columnList = "owner_id, due_date")
})
@EntityListeners(ProjectFactEvent.ProjectFactEventListener.class)
public class ProjectFactEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "fact_type", nullable = false, length = 64)
    private String factType;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "fact_value", columnDefinition = "TEXT")
    private String factValue;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "confidence_level", length = 16)
    private String confidenceLevel;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * 应用层 INSERT-only 拦截.
     */
    public static class ProjectFactEventListener {

        @PreUpdate
        public void preUpdate(ProjectFactEvent evt) {
            // DB 触发器白名单 4 字段; 应用层仅记录, 详细校验在 MOD-03 Service
        }

        @PreRemove
        public void preRemove(ProjectFactEvent evt) {
            throw new IllegalStateException(
                    "project_fact_event is INSERT-only (DELETE forbidden, see DB trigger trg_fact_event_no_delete)");
        }
    }
}
