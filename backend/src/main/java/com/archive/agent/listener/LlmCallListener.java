package com.archive.agent.listener;

import com.archive.entity.LlmCallLog;
import com.archive.repository.LlmCallLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModelListener;
import org.springframework.ai.chat.model.ChatModelResponse;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LlmCallListener implements ChatModelListener {
    private final LlmCallLogRepository repo;

    @Override
    public void onResponse(ChatModelResponse resp) {
        LlmCallLog log = new LlmCallLog();
        log.setScenario("AGENT_STEP");
        log.setModel(resp.getResult().getModel());
        log.setTokensIn((long) resp.getResult().getTokenUsage().getInputTokens());
        log.setTokensOut((long) resp.getResult().getTokenUsage().getOutputTokens());
        repo.save(log);
    }
}
