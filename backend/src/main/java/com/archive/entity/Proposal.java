package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 议案实体 — 项目下的具体审议议题.
 *
 * 议案从属于项目(Project),一个项目可以有多个议案
 * (例如:同一项目下分"主体议案"和"担保议案")。
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
public class Proposal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 议案编号,如 PROP-2026-001-A. */
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /** 议案标题. */
    @Column(name = "title", nullable = false, length = 256)
    private String title;

    /** 所属项目 ID. */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 议案类型:主体 / 担保 / 联合 / 调整 / 终止 / 其他. */
    @Column(name = "type", length = 32)
    private String type;

    /** 摘要(200-500 字). */
    @Column(name = "summary", length = 2000)
    private String summary;

    /** 状态:草稿 / 已提交 / 审议中 / 通过 / 暂缓 / 否决 / 撤回. */
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "草稿";

    /** 审议日期. */
    @Column(name = "reviewed_at")
    private java.time.LocalDate reviewedAt;

    /** 审议结论(投委会填写). */
    @Column(name = "decision", length = 2000)
    private String decision;

    /** 备注. */
    @Column(name = "remark", length = 2000)
    private String remark;
}
