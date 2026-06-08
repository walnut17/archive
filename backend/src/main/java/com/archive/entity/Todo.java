package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 待办实体 — 3 来源汇聚.
 *
 * 待办来自三个渠道:
 * - auto_timepoint:时点到期自动生成
 * - manual:用户手工创建
 * - trigger:触发规则引擎生成
 * 首页"我的待办"查询主路径为 owner_id + status。
 *
 * @author Mavis
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "todo", indexes = {
        @Index(name = "idx_todo_status", columnList = "status"),
        @Index(name = "idx_todo_due", columnList = "due_at"),
        @Index(name = "idx_todo_owner_status", columnList = "owner_id, status"),
        @Index(name = "idx_todo_project", columnList = "project_id"),
        @Index(name = "idx_todo_source", columnList = "source, source_ref_id")
})
public class Todo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 待办标题. */
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /** 来源:auto_timepoint/manual/trigger. */
    @Column(name = "source", nullable = false, length = 16)
    private String source;

    /** 来源 ID(timepoint.id / trigger_rule.id). */
    @Column(name = "source_ref_id")
    private Long sourceRefId;

    /** 关联项目 ID. */
    @Column(name = "project_id")
    private Long projectId;

    /** 责任人 ID. */
    @Column(name = "owner_id")
    private Long ownerId;

    /** 优先级:low/medium/high/urgent. */
    @Column(name = "priority", length = 16)
    @Builder.Default
    private String priority = "medium";

    /** 状态:pending/in_progress/done/cancelled/expired. */
    @Column(name = "status", length = 16)
    @Builder.Default
    private String status = "pending";

    /** 截止时间. */
    @Column(name = "due_at")
    private LocalDateTime dueAt;

    /** 完成时间. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 备注. */
    @Column(name = "remark", length = 1000)
    private String remark;
}
