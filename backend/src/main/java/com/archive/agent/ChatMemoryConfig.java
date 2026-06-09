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
     */
    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}
