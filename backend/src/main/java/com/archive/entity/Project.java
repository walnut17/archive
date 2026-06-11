package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.SQLRestriction;

/**
 * 项目实体 — 投委会审议的项目单元.
 *
 * @author Mavis
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "project", indexes = {
        @Index(name = "idx_project_code", columnList = "code", unique = true),
        @Index(name = "idx_project_status", columnList = "status"),
        @Index(name = "idx_project_owner_id", columnList = "owner_id")
})
@SQLRestriction("deleted_at IS NULL")
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "amount_wan")
    private Long amountWan;

    @Column(name = "summary", length = 2000)
    private String summary;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "草稿";

    @Column(name = "scheduled_meeting_at")
    private java.time.LocalDate scheduledMeetingAt;

    @Column(name = "customer_name", length = 256)
    private String customerName;

    @Column(name = "remark", length = 2000)
    private String remark;

    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "archive_status", length = 32)
    private String archiveStatus;
}
