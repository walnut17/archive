package com.archive.agent;

import com.archive.dto.QaResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 多轮对话服务(I-13).
 * 负责从 chat_memory 拉历史 + 拼上下文.
 */
@Service
public class MultiTurnService {

    private final ChatMemory chatMemory;

    public MultiTurnService(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    /**
     * 获取指定 session 的对话历史.
     */
    public List<String> getHistory(String sessionId) {
        return chatMemory.get(sessionId, 10);
    }

    /**
     * 清除指定 session 的对话历史.
     */
    public void clearHistory(String sessionId) {
        chatMemory.clear(sessionId);
    }
}
