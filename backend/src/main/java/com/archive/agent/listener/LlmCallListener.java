package com.archive.agent.listener;

import com.archive.entity.LlmCallLog;
import com.archive.repository.LlmCallLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * LLM 调用埋点 Advisor.
 *
 * Spring AI 1.1 公开 API: 实现 CallAdvisor (不是 ChatModelListener, 那是 1.2 概念).
 * 用途: 记录每次 LLM 调用的 tokens / 模型 / 耗时 到 llm_call_log 表 (用于用量统计 + 异常排查).
 *
 * 注册: 在 AgentConfig 的 ChatClient builder 注册 defaultAdvisors
 *
 * @author Mavis (修 Sisyphus P0-6)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmCallListener implements CallAdvisor {

    private final LlmCallLogRepository repo;

    @Override
    public String getName() {
        return "LlmCallListener";
    }

    @Override
    public int getOrder() {
        // 数字越小越先执行, 这里放最前 (观察 + 记录)
        return 0;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();
        LlmCallLog log = new LlmCallLog();
        log.setScenario("AGENT_STEP");
        try {
            ChatClientResponse response = chain.nextCall(request);
            ChatResponse chatResp = response.chatResponse();
            if (chatResp != null && chatResp.getMetadata() != null) {
                log.setModel(safe(chatResp.getMetadata().getModel()));
                Usage usage = chatResp.getMetadata().getUsage();
                if (usage != null) {
                    log.setPromptTokens(usage.getPromptTokens());
                    log.setCompletionTokens(usage.getCompletionTokens());
                    log.setTotalTokens(usage.getTotalTokens());
                }
            }
            log.setDurationMs((int) (System.currentTimeMillis() - start));
            log.setStatus("SUCCESS");
            try {
                repo.save(log);
            } catch (Exception e) {
                LlmCallListener.log.warn("LlmCallLog 保存失败: {}", e.getMessage());
            }
            return response;
        } catch (Exception e) {
            log.setDurationMs((int) (System.currentTimeMillis() - start));
            log.setStatus("FAIL");
            log.setErrorMessage(truncate(e.getMessage(), 500));
            try {
                repo.save(log);
            } catch (Exception ignored) {
                // 不递归
            }
            throw e;
        }
    }

    private String safe(String s) {
        return s == null ? "unknown" : s;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
