package com.archive.dto;

import com.archive.agent.AgentResponse;
import com.archive.agent.AgentStep;
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

    /** Agent 模式标记(null=老路径, false=降级, true=Agent). */
    private Boolean agentMode;

    /** Agent ReAct 循环步骤(老路径时 null). */
    private List<AgentStep> steps;

    /** Agent 工具调用次数(老路径时 null). */
    private Integer toolCalls;

    /**
     * 从 AgentResponse 转换为 QaResponse(兼容老前端).
     */
    public static QaResponse fromAgentResponse(AgentResponse ar) {
        return QaResponse.builder()
                .question(null)
                .answer(ar.getAnswer())
                .sources(null)
                .reranked(false)
                .agentMode(true)
                .steps(ar.getSteps())
                .toolCalls(ar.getSteps() != null ? ar.getSteps().size() : 0)
                .build();
    }
}
