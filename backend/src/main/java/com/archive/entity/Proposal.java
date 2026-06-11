package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * 议案实体 — 项目下的具体审议议题.
 *
 * @author Mavis
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "proposal", indexes = {
        @Index(name = "idx_proposal_project_id", columnList = "project_id"),
        @Index(name = "idx_proposal_code", columnList = "code", unique = true),
        @Index(name = "idx_proposal_status", columnList = "status")
})
@SQLRestriction("deleted_at IS NULL")
public class Proposal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "type", length = 32)
    private String type;

    @Column(name = "summary", length = 2000)
    private String summary;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "草稿";

    @Column(name = "reviewed_at")
    private java.time.LocalDate reviewedAt;

    @Column(name = "decision", length = 2000)
    private String decision;

    @Lob
    @Column(name = "condition_text")
    private String conditionText;

    @Column(name = "condition_status", nullable = false, length = 16)
    @Builder.Default
    private String conditionStatus = "NONE";

    @Column(name = "condition_met_at")
    private LocalDateTime conditionMetAt;

    @Column(name = "remark", length = 2000)
    private String remark;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;
}
