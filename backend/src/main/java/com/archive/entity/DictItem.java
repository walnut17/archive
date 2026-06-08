package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 字典项实体 — 字典的具体枚举值.
 *
 * 关联 DictType.type_code,存储下拉框/单选按钮的选项数据。
 * type_code 故意不设外键约束,允许字典类型删除前的孤儿项存在。
 * (type_code, item_key) 有唯一约束防重复。
 * 查询主路径为 type_code + enabled + sort_order。
 *
 * @author Mavis
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "dict_item", indexes = {
        @Index(name = "idx_di_type_enabled", columnList = "type_code, enabled, sort_order")
})
public class DictItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联 dict_type.type_code(不外键,业务灵活). */
    @Column(name = "type_code", nullable = false, length = 64)
    private String typeCode;

    /** 枚举值,如 股权类/草稿. */
    @Column(name = "item_key", nullable = false, length = 64)
    private String itemKey;

    /** 展示值(默认与 item_key 相同). */
    @Column(name = "item_value", nullable = false, length = 256)
    private String itemValue;

    /** 排序序号. */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    /** 新建时是否默认选中. */
    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    /** 是否启用. */
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    /** 系统内置(不可删). */
    @Column(name = "is_system")
    @Builder.Default
    private Boolean isSystem = false;

    /** 备注. */
    @Column(name = "remark", length = 500)
    private String remark;
}
