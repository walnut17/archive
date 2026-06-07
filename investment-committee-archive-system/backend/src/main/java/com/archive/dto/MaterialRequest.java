package com.archive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 材料请求 DTO.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialRequest {

    @NotNull(message = "所属议案 ID 不能为空")
    private Long proposalId;

    @NotBlank(message = "材料标题不能为空")
    @Size(max = 256)
    private String title;

    @Size(max = 64)
    private String category;

    @Size(max = 32)
    private String status;

    @Size(max = 1000)
    private String description;

    @Size(max = 500)
    private String tags;
}
