package com.archive.agent.listener;

import com.archive.entity.AuditLog;
import com.archive.repository.AuditLogRepository;
import com.archive.repository.LlmCallLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModelListener;
import org.springframework.ai.chat.model.ChatModelResponse;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ToolCallListener implements ChatModelListener {
    private final LlmCallLogRepository llmRepo;
    private final AuditLogRepository auditRepo;

    @Override
    public void onResponse(ChatModelResponse resp) {
        // Tool call logging - implementation will be expanded in later tasks
    }
}
