package com.archive.agent;

import com.archive.agent.prompt.AgentSystemPrompt;
import com.archive.agent.tool.AgentTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "spring.ai.agent.enabled", havingValue = "true", matchIfMissing = true)
public class AgentConfig {

    @Bean
    public ChatClient chatClient(org.springframework.ai.openai.OpenAiChatModel model,
                                 List<AgentTool> agentTools,
                                 AgentSystemPrompt systemPrompt) {
        return ChatClient.builder(model)
                .defaultSystem(systemPrompt.render())
                .defaultTools(agentTools.toArray(new AgentTool[0]))
                .build();
    }

    /**
     * Spring AI 1.1 公开 API: JdbcChatMemoryRepository 代替不存在的 JdbcChatMemory class
     * (踩坑预警: Spring AI 1.1 公开 API 没 JdbcChatMemory class, 实际类是 JdbcChatMemoryRepository,
     *  包名: org.springframework.ai.chat.memory.repository.jdbc)
     */
    @Bean
    public JdbcChatMemoryRepository jdbcChatMemoryRepository(DataSource dataSource) {
        return JdbcChatMemoryRepository.builder()
                .dataSource(dataSource)
                .tableName("chat_memory")
                .build();
    }

    /**
     * ChatMemory 默认实现: MessageWindowChatMemory (滑动窗口保留最近 N 条)
     */
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}
