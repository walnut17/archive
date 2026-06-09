package com.archive.agent.prompt;

import org.springframework.stereotype.Component;

/**
 * Agent few-shot 示例.
 * 为 LLM 提供 ReAct 循环的输入/输出示例.
 * 详细实现在 T-I-9 完成.
 */
@Component
public class AgentFewShots {

    public String render() {
        return """
            示例 1:
            用户: 新能源那个项目今年盈利怎么样?
            思考: 用户要找"新能源"相关项目，我需要先用 find_project 定位项目。
            工具: find_project({"query": "新能源"})
            观察: 找到 PRJ-2026-001 (新能源项目), 置信度 0.95
            
            思考: 已定位项目 PRJ-2026-001，现在需要查询其业务数据来获取盈利信息。
            工具: get_project_business_data({"projectCode": "PRJ-2026-001"})
            观察: 项目 PRJ-2026-001 当前余额 5000 万，已放款 2 亿，总金额 2.5 亿
            """;
    }
}
