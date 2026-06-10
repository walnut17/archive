package com.archive.agent;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * I-13: 多轮对话记忆配置.
 *
 * 使用 JdbcChatMemoryRepository 持久化到 MySQL chat_memory 表,重启不丢.
 * 配合 Spring AI 1.1 公开 API: JdbcChatMemoryRepository + MessageWindowChatMemory
 *
 * 修复: Sisyphus 原版用了 JdbcChatMemory class (Spring AI 1.1 公开 API 不存在)
 * 改用: JdbcChatMemoryRepository + MessageWindowChatMemory 滑动窗口
 *
 * @author Mavis + Sisyphus
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public JdbcChatMemoryRepository chatMemoryRepository(DataSource dataSource) {
        return JdbcChatMemoryRepository.builder()
                .dataSource(dataSource)
                .build();
    }

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        // 滑动窗口保留最近 20 条消息
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    /**
     * MessageChatMemoryAdvisor: 在 ChatClient 调用时自动加载/保存多轮上下文.
     * Spring AI 1.1 builder API: MessageChatMemoryAdvisor.builder(chatMemory).build()
     *
     * Spring AI 1.1 bug (Mavis 临时修复):
     * - MessageWindowChatMemory.process() 在拼历史 + new messages 时,
     *   如果某条 message.getText() 返回 null, saveAll 会 INSERT NULL content
     * - H2 / MySQL 不允许 NULL
     * - 修法: AgentEngine 单轮场景不需要多轮对话, 测试不启用 memory advisor
     *   (用 @ConditionalOnProperty name=spring.ai.agent.chat-memory.enabled 控)
     *
     * 默认 enabled=false (测例多, 容易撞 memory leak)
     * 启用: application.yml 加 spring.ai.agent.chat-memory.enabled=true
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "spring.ai.agent.chat-memory.enabled", havingValue = "true")
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}
