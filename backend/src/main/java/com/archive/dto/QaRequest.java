package com.archive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 知识库问答请求 DTO.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QaRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500)
    private String question;

    /** 检索 top N(默认 10). */
    private Integer topN;

    /** 是否用 LLM 重排(默认 true;apiKey 为空时自动跳过). */
    private Boolean rerank;
}
