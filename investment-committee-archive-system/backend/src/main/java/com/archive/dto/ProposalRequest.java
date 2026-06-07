package com.archive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;

/**
 * 议案请求 DTO.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProposalRequest {

    @NotBlank(message = "议案编号不能为空")
    @Size(max = 64)
    private String code;

    @NotBlank(message = "议案标题不能为空")
    @Size(max = 256)
    private String title;

    @NotNull(message = "所属项目 ID 不能为空")
    private Long projectId;

    @Size(max = 32)
    private String type;

    @Size(max = 2000)
    private String summary;

    @Size(max = 32)
    private String status;

    private LocalDate reviewedAt;

    @Size(max = 2000)
    private String decision;

    @Size(max = 2000)
    private String remark;
}
