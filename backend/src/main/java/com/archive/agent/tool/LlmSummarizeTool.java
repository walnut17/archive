package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import com.archive.provider.LLMProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class LlmSummarizeTool implements AgentTool {

    private final LLMProvider llmProvider;

    @Override
    public String name() {
        return "llm_summarize";
    }

    @Override
    public String description() {
        return "对长文本进行 LLM 摘要,适合当工具返回数据过多时,让 LLM 帮你浓缩成给用户的最终答案。";
    }

    @Override
    public Class<?> argsClass() {
        return SummarizeArgs.class;
    }

    @Override
    public ToolResult execute(Object args, AgentContext ctx) {
        SummarizeArgs summarizeArgs = (SummarizeArgs) args;
        String prompt = "请对以下内容进行摘要(不超过" + summarizeArgs.maxLength + "字):\n\n" + summarizeArgs.content;
        String summary = llmProvider.chat("", prompt);
        return ToolResult.ok(Map.of(
                "summary", summary,
                "originalLength", summarizeArgs.content.length()
        ));
    }

    @Data
    public static class SummarizeArgs {
        @JsonProperty("content") String content;
        @JsonProperty("maxLength") Integer maxLength = 500;
    }
}
