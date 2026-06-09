package com.archive.agent.listener;

import com.archive.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.stereotype.Component;

/**
 * 工具调用审计 Advisor.
 *
 * Spring AI 1.1 公开 API: 实现 CallAdvisor.
 * 用途: 记录 LLM 触发的工具调用到 audit_log (合规审计).
 *
 * @author Mavis (修 Sisyphus P0-6)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolCallListener implements CallAdvisor {

    private final AuditLogRepository auditRepo;

    @Override
    public String getName() {
        return "ToolCallListener";
    }

    @Override
    public int getOrder() {
        return 10;  // LlmCallListener 之后
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);

        // 工具调用审计日志 (MVP: 简单记录响应里有 tool call)
        if (response != null && response.chatResponse() != null
                && response.chatResponse().hasToolCalls()) {
            try {
                // 详细审计: tool 名 + 参数 + 结果, 由 Plan I-9 阶段负责完整实现
                // 此处仅打日志, audit_log 落库在 I-9 时补
                log.info("[ToolCall] tool call detected in LLM response");
            } catch (Exception e) {
                log.warn("Tool call audit failed: {}", e.getMessage());
            }
        }

        return response;
    }
}
