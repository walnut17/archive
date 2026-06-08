package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 字段抽取方法实体 — 用户可自定义的 LLM 抽取配置.
 *
 * 定义 LLM 从材料/议案中抽取结构化字段的方法,
 * 包括 Prompt 模板和期望的 JSON Schema。
 * 预置 3 个内置方法: DEFAULT_PROJECT_FIELDS / DEFAULT_TIMEPOINT / DEFAULT_PROPOSAL_SUMMARY。
 * 用户可在管理界面自行新增抽取方法。
 *
 * @author Mavis
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "extraction_method", indexes = {
        @Index(name = "idx_em_code", columnList = "code"),
        @Index(name = "idx_em_apply_enabled", columnList = "apply_to, enabled")
})
public class ExtractionMethod extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 方法代码(全局唯一). */
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /** 方法名称. */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** 方法说明. */
    @Column(name = "description", length = 1000)
    private String description;

    /** 应用对象:material(材料)/proposal(议案). */
    @Column(name = "apply_to", length = 32)
    @Builder.Default
    private String applyTo = "material";

    /** LLM Prompt 模板,${material_title} ${material_content} 等变量. */
    @Lob
    @Column(name = "prompt_template", nullable = false, columnDefinition = "TEXT")
    private String promptTemplate;

    /** 期望输出 JSON Schema. */
    @Column(name = "output_schema", nullable = false, columnDefinition = "JSON")
    private String outputSchema;

    /** 是否系统内置. */
    @Column(name = "builtin")
    @Builder.Default
    private Boolean builtin = false;

    /** 是否启用. */
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    /** 排序序号. */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
}
