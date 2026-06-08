package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 材料实体 — 议案下的具体文件.
 *
 * 一个议案下挂多个材料(尽调报告 / 法律意见书 / 财务审计等)。
 * 一个材料可以有多个版本(MaterialVersion),通过 currentVersionId 指向"当前生效版本"。
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
public class Material extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属议案 ID. */
    @Column(name = "proposal_id", nullable = false)
    private Long proposalId;

    /** 材料标题(显示用,如"尽调报告-终版"). */
    @Column(name = "title", nullable = false, length = 256)
    private String title;

    /** 材料类别:尽调报告 / 法律意见 / 财务审计 / 风险评估 / 投委会决议 / 其他. */
    @Column(name = "category", length = 64)
    private String category;

    /** 当前生效版本 ID(指向 material_version.id,冗余字段方便查询). */
    @Column(name = "current_version_id")
    private Long currentVersionId;

    /** 状态:草稿 / 评审中 / 已通过 / 已归档 / 已作废. */
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "草稿";

    /** 简短说明. */
    @Column(name = "description", length = 1000)
    private String description;

    /** 标签(JSON 数组字符串,逗号分隔,简单起见). */
    @Column(name = "tags", length = 500)
    private String tags;
}
