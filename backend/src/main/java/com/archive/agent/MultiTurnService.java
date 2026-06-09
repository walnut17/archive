package com.archive.agent;

import com.archive.dto.QaResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 多轮对话服务(I-13).
 * 负责从 chat_memory 拉历史 + 拼上下文.
 *
 * Spring AI 1.1 公开 API: ChatMemory.get(String) 返回 List&lt;Message&gt;
 * (Sisyphus 用了过时的 get(String, int) - Mavis 修)
 */
@Service
public class MultiTurnService {

    private final ChatMemory chatMemory;

    public MultiTurnService(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    /**
     * 获取指定 session 的对话历史 (取最后 10 条).
     */
    public List<String> getHistory(String sessionId) {
        List<Message> messages = chatMemory.get(sessionId);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        // 取最后 10 条
        int start = Math.max(0, messages.size() - 10);
        return messages.subList(start, messages.size()).stream()
                .map(Message::getText)
                .collect(Collectors.toList());
    }

    /**
     * 清除指定 session 的对话历史.
     */
    public void clearHistory(String sessionId) {
        chatMemory.clear(sessionId);
    }
}
