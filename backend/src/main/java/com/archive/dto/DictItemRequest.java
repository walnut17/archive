package com.archive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 字典项请求 DTO — 创建用.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DictItemRequest {

    @NotBlank(message = "字典类型代码不能为空")
    @Size(max = 64)
    private String typeCode;

    @NotBlank(message = "字典项键值不能为空")
    @Size(max = 64)
    private String itemKey;

    @NotBlank(message = "字典项显示值不能为空")
    @Size(max = 256)
    private String itemValue;

    /** 排序序号. */
    private Integer sortOrder;

    /** 新建时是否默认选中. */
    private Boolean isDefault;

    /** 是否启用. */
    private Boolean enabled;

    /** 备注. */
    @Size(max = 500)
    private String remark;
}
