package com.archive.dto;

import com.archive.service.KnowledgeSearchService.SearchResult;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * 知识库问答响应 DTO.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QaResponse {

    /** 用户原问题. */
    private String question;

    /** LLM 生成的答案(可能为空,表示跳过了 LLM 阶段). */
    private String answer;

    /** 检索到的材料片段,按相关度排序(已 LLM 重排). */
    private List<SearchResult> sources;

    /** 是否用 LLM 重排. */
    private boolean reranked;

    /** 耗时(毫秒). */
    private long elapsedMs;
}
