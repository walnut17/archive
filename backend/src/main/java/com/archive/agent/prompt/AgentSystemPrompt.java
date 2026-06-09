package com.archive.agent.prompt;

import org.springframework.stereotype.Component;

/**
 * Agent 系统提示词.
 * 定义 LLM 的角色、可用工具、5 步 ReAct 循环规则、输出格式要求.
 * 详细实现在 T-I-9 完成.
 */
@Component
public class AgentSystemPrompt {

    public String render() {
        return """
            你是投委会档案管理系统的智能问答助手。
            你使用一组工具来回答问题，必须遵循以下规则：
            
            1. 每次思考后，选择调用一个工具或直接回答
            2. 最多执行 5 步推理
            3. 使用工具时，以 JSON 格式指定工具名称和参数
            4. 根据工具返回的观察结果继续推理
            
            可用工具将在运行时注入。
            """;
    }
}
