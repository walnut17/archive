package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 项目实体 — 投委会审议的项目单元.
 *
 * 一个项目包含多个议案(Proposal),
 * 议案下挂材料(Material)和材料版本(MaterialVersion)。
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
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 项目编号,如 PRJ-2026-001. */
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /** 项目名称. */
    @Column(name = "name", nullable = false, length = 256)
    private String name;

    /** 业务类别,如 股权类 / 固收类 / 混合类 / 其他. */
    @Column(name = "category", length = 64)
    private String category;

    /** 项目经理 ID(关联 user.id). */
    @Column(name = "owner_id")
    private Long ownerId;

    /** 投资金额(单位:万元),用于概览展示. */
    @Column(name = "amount_wan")
    private Long amountWan;

    /** 摘要(项目经理手填,200-500 字). */
    @Column(name = "summary", length = 2000)
    private String summary;

    /** 状态:草稿 / 待审议 / 审议中 / 通过 / 暂缓 / 否决 / 撤回. */
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "草稿";

    /** 投委会审议日期(已排期). */
    @Column(name = "scheduled_meeting_at")
    private java.time.LocalDate scheduledMeetingAt;

    /** 备注(自由文本). */
    @Column(name = "remark", length = 2000)
    private String remark;
}
