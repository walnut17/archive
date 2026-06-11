package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 对比方法实体 — 用户可自定义的 LLM 对比配置.
 *
 * 定义 LLM 对比两份报告(如立项报告 vs 申请报告)的方法,
 * 包括源/目标报告类型、Prompt 模板和输出的 JSON Schema。
 * 预置 1 个内置方法: DEFAULT_QA_VERIFY(Q&A 验证待落实问题)。
 * 用户可在管理界面自行新增对比方法。
 *
 * @author Mavis
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "comparison_method", indexes = {
        @Index(name = "idx_cm_code", columnList = "code"),
        @Index(name = "idx_cm_from_to", columnList = "from_type, to_type, enabled")
})
public class ComparisonMethod extends BaseEntity {

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

    /** 源报告类型. */
    @Column(name = "from_type", length = 32)
    @Builder.Default
    private String fromType = "立项";

    /** 目标报告类型. */
    @Column(name = "to_type", length = 32)
    @Builder.Default
    private String toType = "申请";

    /** LLM Prompt 模板. */
    @Lob
    @Column(name = "prompt_template", nullable = false)
    private String promptTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_schema", nullable = false)
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
