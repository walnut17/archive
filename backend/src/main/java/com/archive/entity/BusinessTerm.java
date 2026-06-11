package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 业务术语字典 — 支持软删.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "business_term", indexes = {
        @Index(name = "idx_bt_name", columnList = "name"),
        @Index(name = "idx_bt_status", columnList = "status")
})
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(BusinessTerm.BusinessTermListener.class)
public class BusinessTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "english_name", length = 128)
    private String englishName;

    @Column(name = "aliases", length = 512)
    private String aliases;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "definition", columnDefinition = "TEXT")
    private String definition;

    @Column(name = "standard_definition", columnDefinition = "TEXT")
    private String standardDefinition;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    @Column(name = "data_mapping", columnDefinition = "JSON")
    private String dataMapping;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "draft";

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

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 软删拦截 — 禁止物理删除, 应使用 deleted_at 标记.
     */
    public static class BusinessTermListener {

        @PreRemove
        public void preRemove(BusinessTerm term) {
            throw new IllegalStateException("business_term 不可物理删除, 请使用软删(deleted_at)");
        }
    }
}
