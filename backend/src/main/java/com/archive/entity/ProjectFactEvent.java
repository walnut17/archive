package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

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

    private static final Set<String> UPDATABLE_FIELDS = Set.of(
            "ownerId", "dueDate", "resolvedAt", "resolutionNote"
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "fact_type", nullable = false, length = 64)
    private String factType;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Lob
    @Column(name = "fact_value")
    private String factValue;

    @Lob
    @Column(name = "evidence")
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

    @Lob
    @Column(name = "resolution_note")
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

    @Transient
    private transient Long snapshotProjectId;
    @Transient
    private transient String snapshotFactType;
    @Transient
    private transient String snapshotEventType;
    @Transient
    private transient String snapshotFactValue;
    @Transient
    private transient String snapshotEvidence;
    @Transient
    private transient BigDecimal snapshotConfidence;
    @Transient
    private transient String snapshotConfidenceLevel;
    @Transient
    private transient Long snapshotOwnerId;
    @Transient
    private transient LocalDate snapshotDueDate;
    @Transient
    private transient LocalDateTime snapshotResolvedAt;
    @Transient
    private transient String snapshotResolutionNote;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    void captureUpdateSnapshot() {
        snapshotProjectId = projectId;
        snapshotFactType = factType;
        snapshotEventType = eventType;
        snapshotFactValue = factValue;
        snapshotEvidence = evidence;
        snapshotConfidence = confidence;
        snapshotConfidenceLevel = confidenceLevel;
        snapshotOwnerId = ownerId;
        snapshotDueDate = dueDate;
        snapshotResolvedAt = resolvedAt;
        snapshotResolutionNote = resolutionNote;
    }

    void assertOnlyWhitelistedFieldsChanged() {
        assertFieldUnchanged("projectId", projectId, snapshotProjectId);
        assertFieldUnchanged("factType", factType, snapshotFactType);
        assertFieldUnchanged("eventType", eventType, snapshotEventType);
        assertFieldUnchanged("factValue", factValue, snapshotFactValue);
        assertFieldUnchanged("evidence", evidence, snapshotEvidence);
        assertFieldUnchanged("confidence", confidence, snapshotConfidence);
        assertFieldUnchanged("confidenceLevel", confidenceLevel, snapshotConfidenceLevel);
    }

    private void assertFieldUnchanged(String fieldName, Object current, Object snapshot) {
        if (UPDATABLE_FIELDS.contains(fieldName)) {
            return;
        }
        if (!Objects.equals(current, snapshot)) {
            throw new IllegalStateException(
                    "project_fact_event field '" + fieldName + "' is not updatable "
                            + "(whitelist: ownerId, dueDate, resolvedAt, resolutionNote)");
        }
    }

    /**
     * 应用层 INSERT-only 拦截 + UPDATE 白名单 (RI-46/52).
     */
    public static class ProjectFactEventListener {

        @PostLoad
        public void postLoad(ProjectFactEvent evt) {
            evt.captureUpdateSnapshot();
        }

        @PreUpdate
        public void preUpdate(ProjectFactEvent evt) {
            evt.assertOnlyWhitelistedFieldsChanged();
        }

        @PreRemove
        public void preRemove(ProjectFactEvent evt) {
            throw new IllegalStateException(
                    "project_fact_event is INSERT-only (DELETE forbidden, see DB trigger trg_fact_event_no_delete)");
        }
    }
}
