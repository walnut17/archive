package com.archive.agent;

import com.archive.agent.prompt.AgentSystemPrompt;
import com.archive.agent.tool.AgentTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.jdbc.JdbcChatMemory;
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

    @Bean
    public JdbcChatMemory jdbcChatMemory(DataSource dataSource) {
        return new JdbcChatMemory(dataSource);
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(JdbcChatMemory chatMemory) {
        return new MessageChatMemoryAdvisor(chatMemory);
    }
}
