package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AskClarificationTool implements AgentTool {

    @Override
    public String name() {
        return "ask_clarification";
    }

    @Override
    public String description() {
        return "当用户的问题有歧义或信息不足时,向用户追问澄清。返回的 question 会作为后续步骤的上下文。";
    }

    @Override
    public Class<?> argsClass() {
        return ClarificationArgs.class;
    }

    @Override
    public ToolResult execute(Object args, AgentContext ctx) {
        ClarificationArgs clarificationArgs = (ClarificationArgs) args;
        return ToolResult.ok(Map.of(
                "question", clarificationArgs.question,
                "options", clarificationArgs.options != null ? clarificationArgs.options : List.of()
        ));
    }

    @Data
    public static class ClarificationArgs {
        @JsonProperty("question") String question;
        @JsonProperty("options") List<String> options;
    }
}
