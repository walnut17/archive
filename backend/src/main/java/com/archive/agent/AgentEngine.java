package com.archive.agent;

import com.archive.agent.prompt.AgentSystemPrompt;
import com.archive.agent.tool.AgentTool;
import com.archive.agent.tool.ToolResult;
import com.archive.common.SwitchDecision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 核心 Agent 引擎:手写 5 步 ReAct 循环.
 * MAX_ITERATIONS 硬编码 = 5,无配置项.
 *
 * @deprecated 自 v1.1 起 Java Agent 不再维护，由 Python qa-agent 微服务取代。
 *     保留仅用于降级路径（spring.ai.agent.enabled=true 时加载）。
 *     后续版本将移除此实现。
 */
@Service
@Deprecated
@ConditionalOnProperty(name = "spring.ai.agent.enabled", havingValue = "true", matchIfMissing = true)
public class AgentEngine {

    private static final Logger log = LoggerFactory.getLogger(AgentEngine.class);
    private static final int MAX_ITERATIONS = 5;
    private static final String FINAL_ANSWER = "FINAL_ANSWER";
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 死循环触发阈值: 连续 (loopTriggerThreshold) 步 (tool, toolArgs) 完全相同 = 死循环.
     * 1 = 立即死循环 (严); 2 = 宽容 1 步再判 (宽). 默认 1.
     */
    @org.springframework.beans.factory.annotation.Value("${spring.ai.agent.find-project.loop-trigger-threshold:1}")
    private int loopTriggerThreshold;

    private final ChatClient chatClient;
    private final AgentSystemPrompt systemPrompt;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final Map<String, AgentTool> toolMap;

    public AgentEngine(ChatClient.Builder builder,
                       AgentSystemPrompt systemPrompt,
                       List<AgentTool> agentTools,
                       org.springframework.beans.factory.ObjectProvider<MessageChatMemoryAdvisor> memoryAdvisorProvider) {
        this.systemPrompt = systemPrompt;
        this.memoryAdvisor = memoryAdvisorProvider.getIfAvailable();
        this.toolMap = new ConcurrentHashMap<>();
        for (AgentTool tool : agentTools) {
            toolMap.put(tool.name(), tool);
        }
        // 不传 defaultTools: AgentEngine 手写 ReAct 循环, 工具由 toolMap 自己 dispatch
        // (Mavis 修 P0-15: Sisyphus 误用 defaultTools(AgentTool[]), 1.1 公开 API 期望 @Tool 注解)
        this.chatClient = builder
                .defaultSystem(systemPrompt.render(null))
                .build();
    }

    /**
     * 执行 ReAct 循环.
     */
    public AgentResponse run(AgentRequest request) {
        String question = request.getQuestion();
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";
        AgentContext ctx = new AgentContext(question);
        List<String> sources = new ArrayList<>();
        List<com.archive.dto.Source> agentSources = new ArrayList<>();
        String finalAnswer = null;
        int loopHintCount = 0; // 本次请求的死循环提示计数

        for (int i = 1; i <= MAX_ITERATIONS; i++) {
            log.info("[Agent] Iteration {}/{}", i, MAX_ITERATIONS);

            // 1) 构建 prompt
            String prompt = buildPrompt(question, sessionId, ctx);

            // 2) 调用 LLM
            String llmResponse = callLlm(prompt);
            log.info("[Agent] LLM response: {}", truncate(llmResponse, 500));

            // 3) 解析 agent step
            AgentStep step = parseAgentStep(llmResponse, i);
            ctx.addStep(step);

            // 4) 终止判断
            if (FINAL_ANSWER.equals(step.getTool())) {
                finalAnswer = extractFinalAnswer(step.getToolArgs());
                if (step.getToolArgs() != null) {
                    JsonNode argsNode = parseJson(step.getToolArgs());
                    if (argsNode != null && argsNode.has("sources")) {
                        JsonNode srcNode = argsNode.get("sources");
                        if (srcNode.isArray()) {
                            for (JsonNode s : srcNode) {
                                sources.add(s.asText());
                            }
                        }
                    }
                }
                break;
            }

            // 4.5) 死循环检测: 连续 (loopTriggerThreshold) 步 (tool, toolArgs) 完全相同 -> 强制 LLM 总结
            if (isLoopingStep(ctx.getSteps())) {
                loopHintCount++;
                if (loopHintCount == 1) {
                    // 第一次触发死循环 -> 注入 hint, 给 LLM 一次换策略的机会
                    log.warn("[Agent] 死循环触发 (第 1 次, 阈值={}), 注入 hint. tool={}", loopTriggerThreshold, step.getTool());
                    String hint = String.format(
                            "你已连续 %d 次用相同的工具 (%s) 和相同的参数得到相同结果. 请立即: "
                          + "1) 换不同关键词重新查询, 或 2) 换不同工具, 或 3) 直接给出 FINAL_ANSWER 说明情况",
                            loopTriggerThreshold, step.getTool());
                    step.setObservation(hint);
                    continue; // 跳过本轮 dispatch, 下一轮 LLM 看到 hint 后做选择
                }
                // 第 2+ 次死循环 -> 强制 FINAL_ANSWER
                log.warn("[Agent] 死循环触发 (第 {} 次), 强制 FINAL_ANSWER. tool={}", loopHintCount, step.getTool());
                step.setThought(String.format("连续 %d 次死循环, 强制结束", loopHintCount));
                step.setTool(FINAL_ANSWER);
                step.setToolArgs("{\"answer\":\"抱歉, 多次尝试未找到匹配结果. 请尝试换更具体的关键词, 或直接联系档案管理员.\"}");
                finalAnswer = "抱歉, 多次尝试未找到匹配结果. 请尝试换更具体的关键词, 或直接联系档案管理员.";
                break;
            }

            // 5) 执行工具
            ToolResult result = dispatchTool(step.getTool(), step.getToolArgs(), ctx);
            String observation = result.isOk()
                    ? (result.getData() != null ? result.getData().toString() : "OK")
                    : "ERROR: " + result.getError();
            step.setObservation(truncate(observation, 2000));

            // 5.5) 收集工具调用的来源区数据（plan-source-display）
            // 优先用工具显式返回的 sources，否则从 data 形状推断
            List<com.archive.dto.Source> toolSources = result.getSources();
            if (result.isOk() && (toolSources == null || toolSources.isEmpty())) {
                toolSources = extractSources(step.getTool(), result.getData());
            }
            if (toolSources != null) {
                for (com.archive.dto.Source src : toolSources) {
                    boolean dup = agentSources.stream()
                            .anyMatch(s -> s.getType() == src.getType() && s.getId().equals(src.getId()));
                    if (!dup) {
                        agentSources.add(src);
                    }
                }
            }

            // 6) 记录工具调用日志
            logToolCall(step, question);
        }

        // 兜底:循环结束仍无 final answer
        if (finalAnswer == null) {
            finalAnswer = "抱歉,我无法在 5 步内找到答案。请尝试更具体的提问。";
        }

        AgentResponse response = new AgentResponse();
        response.setAnswer(finalAnswer);
        response.setSteps(ctx.getSteps());
        response.setSources(sources);
        response.setAgentSources(agentSources.isEmpty() ? null : agentSources);
        response.setAgentMode(true);
        populateV11Fields(response, ctx);
        return response;
    }

    /** v1.1: 从 find_project 隐式切换判定填充前端 hint / 置信度徽章. */
    private void populateV11Fields(AgentResponse response, AgentContext ctx) {
        SwitchDecision decision = ctx.getLastSwitchDecision();
        if (decision == null) {
            return;
        }
        if (decision != SwitchDecision.SAME_CONFIRMED) {
            response.setProjectSwitchHint(decision.name());
        }
        switch (decision) {
            case SAME_PROBABLY, DIFFERENT_PROBABLY -> response.setConfidenceBadge("AI_INFERRED");
            case UNCLEAR -> response.setConfidenceBadge("PENDING_REVIEW");
            default -> { /* SAME_CONFIRMED: 高置信无徽章 */ }
        }
    }

    /**
     * 构建发送给 LLM 的 prompt (含上下文).
     */
    private String buildPrompt(String question, String sessionId, AgentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt.render(ctx));
        sb.append("\n\n用户问题: ").append(question);
        return sb.toString();
    }

    /**
     * 调用 LLM (ChatClient + Memory).
     */
    private String callLlm(String prompt) {
        var spec = chatClient.prompt().user(prompt);
        if (memoryAdvisor != null) {
            spec = spec.advisors(memoryAdvisor);
        }
        return spec.call().content();
    }

    /**
     * 解析 LLM 输出为 AgentStep.
     * LLM 应输出 JSON: { "thought": "...", "tool": "...", "args": {...} }
     */
    private AgentStep parseAgentStep(String llmOutput, int iteration) {
        AgentStep step = new AgentStep();
        step.setIteration(iteration);

        if (llmOutput == null || llmOutput.isBlank()) {
            step.setThought("LLM 返回空");
            step.setTool(FINAL_ANSWER);
            step.setToolArgs("{\"answer\":\"LLM 未返回有效响应\"}");
            return step;
        }

        // 尝试从 LLM 输出中提取 JSON
        String json = extractJson(llmOutput);
        JsonNode node = parseJson(json);

        if (node == null) {
            // 解析失败 → 直接返回 LLM 原文（如离题拒答场景）
            step.setThought("直接返回结果");
            step.setTool(FINAL_ANSWER);
            step.setToolArgs("{\"answer\":\"" + escapeJson(llmOutput) + "\"}");
            return step;
        }

        step.setThought(node.has("thought") ? node.get("thought").asText() : "");
        step.setTool(node.has("tool") ? node.get("tool").asText() : FINAL_ANSWER);
        step.setToolArgs(node.has("args") ? node.get("args").toString() : "{}");
        return step;
    }

    /**
     * 分发工具调用.
     */
    private ToolResult dispatchTool(String toolName, String argsJson, AgentContext ctx) {
        AgentTool tool = toolMap.get(toolName);
        if (tool == null) {
            return ToolResult.error("未知工具: " + toolName);
        }

        try {
            Object args = parseToolArgs(argsJson, tool.argsClass());
            return tool.execute(args, ctx);
        } catch (Exception e) {
            log.error("[Agent] Tool execution error: tool={}, error={}", toolName, e.getMessage(), e);
            // 截断错误信息避免把 SQL 全文返给 LLM (P0-20 修)
            String err = e.getMessage();
            if (err == null) err = "未知异常";
            // 只保留第 1 行 (前 200 字)
            int newline = err.indexOf('\n');
            if (newline > 0) err = err.substring(0, newline);
            if (err.length() > 200) err = err.substring(0, 200) + "...";
            return ToolResult.error("工具执行异常: " + err);
        }
    }

    /**
     * 解析工具参数 JSON 为目标类型.
     */
    private Object parseToolArgs(String argsJson, Class<?> argsClass) {
        if (argsJson == null || argsJson.isBlank() || argsJson.equals("{}")) {
            try {
                return argsClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                return null;
            }
        }
        try {
            return mapper.readValue(argsJson, argsClass);
        } catch (Exception e) {
            log.warn("[Agent] Failed to parse tool args: {}", truncate(argsJson, 200));
            return null;
        }
    }

    /**
     * 从 LLM 输出中提取 JSON (支持 markdown 代码块).
     */
    private String extractJson(String text) {
        if (text == null) return null;

        // 先尝试直接解析
        text = text.strip();
        if (text.startsWith("{")) return text;

        // 尝试从 ```json ... ``` 中提取
        int start = text.indexOf("```json");
        if (start >= 0) {
            start += 7;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).strip();
            }
        }

        // 尝试从 ``` ... ``` 中提取
        start = text.indexOf("```");
        if (start >= 0) {
            start += 3;
            // 跳过语言标识
            int langEnd = text.indexOf("\n", start);
            if (langEnd > start) start = langEnd + 1;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).strip();
            }
        }

        // 尝试找第一个 { 到最后一个 }
        int braceStart = text.indexOf('{');
        int braceEnd = text.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1);
        }

        return text;
    }

    /**
     * 安全解析 JSON.
     */
    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            log.warn("[Agent] Failed to parse JSON: {}", truncate(json, 200));
            return null;
        }
    }

    /**
     * 提取 final answer.
     */
    private String extractFinalAnswer(String argsJson) {
        JsonNode node = parseJson(argsJson);
        if (node != null && node.has("answer")) {
            return node.get("answer").asText();
        }
        return argsJson;
    }

    /**
     * 死循环检测: 最近 (loopTriggerThreshold) 个非死循环提示的 step 的 (tool, toolArgs) 完全相同 -> 视为死循环.
     */
    private boolean isLoopingStep(List<AgentStep> steps) {
        int threshold = Math.max(1, loopTriggerThreshold);
        if (steps.size() < threshold) return false;
        AgentStep prev = steps.get(steps.size() - threshold);
        AgentStep curr = steps.get(steps.size() - 1);
        if (prev.getTool() == null || curr.getTool() == null) return false;
        if (FINAL_ANSWER.equals(prev.getTool()) || FINAL_ANSWER.equals(curr.getTool())) return false;
        return Objects.equals(prev.getTool(), curr.getTool())
                && Objects.equals(prev.getToolArgs(), curr.getToolArgs());
    }

    /**
     * 记录工具调用日志.
     */
    private void logToolCall(AgentStep step, String question) {
        log.info("[Agent] Step {}: thought='{}', tool='{}', observation='{}'",
                step.getIteration(),
                truncate(step.getThought(), 100),
                step.getTool(),
                truncate(step.getObservation(), 200));
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 从工具执行结果中提取结构化 Source 条目.
     * 根据工具名称和返回数据形状自动推断来源类型.
     */
    @SuppressWarnings("unchecked")
    private List<com.archive.dto.Source> extractSources(String toolName, Object data) {
        List<com.archive.dto.Source> result = new ArrayList<>();
        if (data == null) return result;

        try {
            switch (toolName) {
                case "find_project" -> {
                    // data = List<FindProjectMatch> (POJO, 不是 Map)
                    var rawList = (List<?>) data;
                    for (Object item : rawList) {
                        // 用 Jackson 将 POJO 转为 Map 以统一访问
                        JsonNode node = mapper.valueToTree(item);
                        result.add(com.archive.dto.Source.builder()
                                .type(com.archive.dto.Source.SourceType.PROJECT)
                                .id(str(node.get("projectCode")))
                                .title(str(node.get("projectName")))
                                .build());
                    }
                }
                case "search_fulltext" -> {
                    // data = List<SearchResult> (POJO, 不是 Map)
                    var rawList = (List<?>) data;
                    for (Object item : rawList) {
                        JsonNode node = mapper.valueToTree(item);
                        result.add(com.archive.dto.Source.builder()
                                .type(com.archive.dto.Source.SourceType.MATERIAL)
                                .id(str(node.get("materialId")))
                                .title(str(node.get("materialTitle")))
                                .snippet(truncate(str(node.get("snippet")), 100))
                                .build());
                    }
                }
                case "get_project_business_data" -> {
                    Map<String, Object> map = (Map<String, Object>) data;
                    result.add(com.archive.dto.Source.builder()
                            .type(com.archive.dto.Source.SourceType.PROJECT)
                            .id(str(map.get("projectCode")))
                            .title(str(map.get("projectName")))
                            .build());
                }
                case "network_dict_lookup" -> {
                    Map<String, Object> map = (Map<String, Object>) data;
                    if (Boolean.TRUE.equals(map.get("found"))) {
                        result.add(com.archive.dto.Source.builder()
                                .type(com.archive.dto.Source.SourceType.TERM)
                                .id(str(map.get("source")))
                                .title(truncate(str(map.get("definition")), 80))
                                .build());
                    }
                }
                case "query_mysql" -> {
                    Map<String, Object> map = (Map<String, Object>) data;
                    // query_mysql 查 project 表时添加 PROJECT 来源
                    result.add(com.archive.dto.Source.builder()
                            .type(com.archive.dto.Source.SourceType.MATERIAL)
                            .id("sql-" + str(map.get("rowCount")))
                            .title("查询结果 " + map.get("rowCount") + " 行")
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("[Agent] extractSources 异常: tool={}, {}", toolName, e.getMessage());
        }
        return result;
    }

    private String str(Object o) { return o == null ? "" : o.toString(); }
    
    /** JsonNode 转 String，处理 null 节点. */
    private String str(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText();
    }
}
