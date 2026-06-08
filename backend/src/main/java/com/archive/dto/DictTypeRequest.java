package com.archive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 字典分类请求 DTO — 创建用.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DictTypeRequest {

    @NotBlank(message = "字典类型代码不能为空")
    @Size(max = 64)
    private String typeCode;

    @NotBlank(message = "字典类型名称不能为空")
    @Size(max = 128)
    private String typeName;

    @Size(max = 500)
    private String description;

    /** 排序序号. */
    private Integer sortOrder;
}
