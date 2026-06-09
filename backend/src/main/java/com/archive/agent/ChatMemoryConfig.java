package com.archive.agent;

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
                .tableName("chat_memory")
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
}
