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
 * 材料实体 — 议案下的具体文件.
 *
 * @author Mavis
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "material", indexes = {
        @Index(name = "idx_material_proposal_id", columnList = "proposal_id"),
        @Index(name = "idx_material_category", columnList = "category"),
        @Index(name = "idx_material_status", columnList = "status")
})
@SQLRestriction("deleted_at IS NULL")
public class Material extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proposal_id", nullable = false)
    private Long proposalId;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "草稿";

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
}
