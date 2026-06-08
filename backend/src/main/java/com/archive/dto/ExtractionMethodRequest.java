package com.archive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 抽取方法请求 DTO — 创建/更新用.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtractionMethodRequest {

    @NotBlank(message = "方法代码不能为空")
    @Size(max = 64)
    private String code;

    @NotBlank(message = "方法名称不能为空")
    @Size(max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    /** 应用对象:material(材料)/proposal(议案). */
    @Size(max = 32)
    private String applyTo;

    @NotBlank(message = "Prompt 模板不能为空")
    private String promptTemplate;

    /** 期望输出 JSON Schema. */
    private String outputSchema;

    /** 是否启用. */
    private Boolean enabled;

    /** 排序序号. */
    private Integer sortOrder;
}
