package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 时点实体 — LLM 抽取或手工填写的关键时间节点.
 *
 * 记录项目生命周期中的到期/审议/披露/付款等关键时间节点,
 * 可由 LLM 从材料中自动抽取,也可由用户手工维护。
 * 通过 reminderDays 字段支持灵活的多级提醒配置。
 *
 * @author Mavis
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "timepoint", indexes = {
        @Index(name = "idx_tp_project", columnList = "project_id"),
        @Index(name = "idx_tp_due", columnList = "due_at"),
        @Index(name = "idx_tp_status", columnList = "status"),
        @Index(name = "idx_tp_type", columnList = "type"),
        @Index(name = "idx_tp_owner", columnList = "owner_id")
})
public class Timepoint extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属项目 ID. */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 来源材料版本 ID(LLM 抽取时填). */
    @Column(name = "material_version_id")
    private Long materialVersionId;

    /** 时点事项描述. */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** 时点类型:到期/审议/披露/付款/法律意见/工商变更/其他. */
    @Column(name = "type", length = 32)
    @Builder.Default
    private String type = "其他";

    /** 截止日期. */
    @Column(name = "due_at", nullable = false)
    private LocalDate dueAt;

    /** 提醒天数(逗号分隔). */
    @Column(name = "reminder_days", length = 64)
    @Builder.Default
    private String reminderDays = "30,7,1,0";

    /** 状态:待提醒/已提醒/已处理/已逾期/已作废. */
    @Column(name = "status", length = 16)
    @Builder.Default
    private String status = "待提醒";

    /** 原文出处(句子级). */
    @Lob
    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    /** 原文页码. */
    @Column(name = "source_page")
    private Integer sourcePage;

    /** 抽取置信度 0~1. */
    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    /** 抽取方式:manual/llm. */
    @Column(name = "extracted_by", length = 16)
    @Builder.Default
    private String extractedBy = "manual";

    /** 责任人 ID(关联 user.id). */
    @Column(name = "owner_id")
    private Long ownerId;

    /** 备注. */
    @Column(name = "remark", length = 1000)
    private String remark;
}
