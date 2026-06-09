package com.archive.agent;

import com.archive.agent.listener.LlmCallListener;
import com.archive.agent.listener.ToolCallListener;
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

    /**
     * ChatClient Bean - Spring AI 1.1
     * 注册 3 个 Advisor: MessageChatMemory (多轮记忆) + LlmCallListener (token 统计) + ToolCallListener (审计)
     * 顺序: LlmCall (order=0) -> ToolCall (order=10) -> Memory (默认)
     */
    @Bean
    public ChatClient chatClient(org.springframework.ai.openai.OpenAiChatModel model,
                                 List<AgentTool> agentTools,
                                 AgentSystemPrompt systemPrompt,
                                 MessageChatMemoryAdvisor memoryAdvisor,
                                 LlmCallListener llmCallListener,
                                 ToolCallListener toolCallListener) {
        return ChatClient.builder(model)
                .defaultSystem(systemPrompt.render(null))  // 初始无上下文
                .defaultTools(agentTools.toArray(new AgentTool[0]))
                .defaultAdvisors(llmCallListener, toolCallListener, memoryAdvisor)
                .build();
    }

    /**
     * Spring AI 1.1 公开 API: JdbcChatMemoryRepository 代替不存在的 JdbcChatMemory class
     * (踩坑预警: Spring AI 1.1 公开 API 没 JdbcChatMemory class, 实际类是 JdbcChatMemoryRepository,
     *  包名: org.springframework.ai.chat.memory.repository.jdbc)
     */
    /**
     * JdbcChatMemoryRepository bean.
     * Spring AI 1.1 公开 API 不提供 tableName() 方法 - 使用默认表名 spring_ai_chat_memory
     * (对应 I-13 迁移脚本 I-chat-memory.sql 中建立的表)
     */
    @Bean
    public JdbcChatMemoryRepository jdbcChatMemoryRepository(DataSource dataSource) {
        return JdbcChatMemoryRepository.builder()
                .dataSource(dataSource)
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
