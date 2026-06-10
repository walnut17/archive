package com.archive.agent;

import com.archive.agent.listener.LlmCallListener;
import com.archive.agent.listener.ToolCallListener;
import com.archive.agent.prompt.AgentSystemPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * ChatClient 配置 + Agent Advisor 注册.
 *
 * **重要**: Sisyphus 误用了 defaultTools(AgentTool[]), Spring AI 1.1 期望 @Tool 注解方法.
 * 修法 (Mavis P0-15): 不传 defaultTools, 因为 AgentEngine 走手写 ReAct 循环,
 * 工具调用由 AgentEngine.toolMap 自己 dispatch, 不依赖 Spring AI tool mechanism.
 *
 * **重要**: 智谱 OpenAI 兼容 endpoint 是 /v4/chat/completions (非标 v4)
 * Spring AI 1.1 OpenAI 协议会硬拼 /v1/chat/completions (404)
 * 修法: 用自定义 GLMChatModel (调项目自家 GlmService) 代替 OpenAiChatModel
 *
 * @author Mavis (修 Sisyphus P0-15)
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.agent.enabled", havingValue = "true", matchIfMissing = true)
public class AgentConfig {

    /**
     * ChatClient Bean.
     * 用 GLMChatModel (ChatModel 接口实现) 代替 OpenAiChatModel
     * 内部调 GlmService -> 直接打智谱 /v4/chat/completions
     *
     * 不传 defaultTools (AgentEngine 手写 ReAct 循环, 工具分发自己搞)
     */
    @Bean
    public ChatClient chatClient(ChatModel model,
                                 AgentSystemPrompt systemPrompt,
                                 LlmCallListener llmCallListener,
                                 ToolCallListener toolCallListener,
                                 org.springframework.beans.factory.ObjectProvider<MessageChatMemoryAdvisor> memoryAdvisor) {
        // memoryAdvisor 是可选的 (测试场景可关掉避免 memory NULL bug)
        java.util.List<org.springframework.ai.chat.client.advisor.api.Advisor> advisors = new java.util.ArrayList<>();
        advisors.add(llmCallListener);
        advisors.add(toolCallListener);
        MessageChatMemoryAdvisor mem = memoryAdvisor.getIfAvailable();
        if (mem != null) advisors.add(mem);

        // 默认 ChatOptions: temperature 0.1 (小模型 ReAct 稳), maxTokens 2048
        // 注: GLMChatModel 实际读 prompt.getOptions(), 这里设 defaultOptions 影响全 chat
        var defaultOpts = new org.springframework.ai.chat.prompt.DefaultChatOptions();
        defaultOpts.setTemperature(0.1);
        defaultOpts.setMaxTokens(2048);

        return ChatClient.builder(model)
                .defaultSystem(systemPrompt.render(null))  // 初始无上下文
                .defaultOptions(defaultOpts)
                .defaultAdvisors(advisors.toArray(new org.springframework.ai.chat.client.advisor.api.Advisor[0]))
                .build();
    }

    /**
     * JdbcChatMemoryRepository bean.
     * Spring AI 1.1 公开 API 不提供 tableName() 方法 - 使用默认表名 spring_ai_chat_memory.
     *
     * 注意: ChatMemory / MessageChatMemoryAdvisor Bean 在 ChatMemoryConfig 里定义
     * 避免重复 Bean 冲突 (Mavis 修 P0-13)
     */
    @Bean
    public JdbcChatMemoryRepository jdbcChatMemoryRepository(DataSource dataSource) {
        return JdbcChatMemoryRepository.builder()
                .dataSource(dataSource)
                .build();
    }
}
