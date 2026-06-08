package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 字典分类实体 — 字典数据分类.
 *
 * 定义系统中可配置的字典分类,如:project_category / project_status /
 * material_category / proposal_type / proposal_status 等。
 * 分类下的具体字典项存储在 DictItem 表中。
 * isSystem 标记的字典分类不可删除。
 *
 * @author Mavis
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "dict_type", indexes = {
        @Index(name = "idx_dt_code", columnList = "type_code")
})
public class DictType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 字典代码:project_category/project_status/material_category/proposal_type/proposal_status. */
    @Column(name = "type_code", nullable = false, unique = true, length = 64)
    private String typeCode;

    /** 显示名:项目类别/项目状态/材料类别/议案类型/议案状态. */
    @Column(name = "type_name", nullable = false, length = 128)
    private String typeName;

    /** 描述. */
    @Column(name = "description", length = 500)
    private String description;

    /** 排序序号. */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    /** 系统内置(不可删). */
    @Column(name = "is_system")
    @Builder.Default
    private Boolean isSystem = false;

    /** 是否启用. */
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;
}
