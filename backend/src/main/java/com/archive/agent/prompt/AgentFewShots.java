package com.archive.agent.prompt;

import org.springframework.stereotype.Component;

/**
 * Agent few-shot 示例.
 * 为 LLM 提供 ReAct 循环的输入/输出示例.
 */
@Component
public class AgentFewShots {

    public static String examples() {
        return """
            示例 1:
            用户: 新能源那个项目今年盈利怎么样?
            思考: 用户要找"新能源"相关项目，我需要先用 find_project 定位项目。
            工具: find_project, args: {"query": "新能源", "topN": 3}
            观察: [{"projectCode":"PRJ-2026-001","projectName":"新能源项目","customerName":"某新能源公司","confidence":0.95}]
            
            思考: 已定位项目 PRJ-2026-001，现在需要查询其业务数据来获取盈利信息。
            工具: get_project_business_data, args: {"projectCode": "PRJ-2026-001"}
            观察: {"projectCode":"PRJ-2026-001","name":"新能源项目","stage":"贷后","amountWan":5000,"remainingAmountWan":3200,"openTodoCount":3,"customerName":"某新能源公司"}
            
            思考: 已获取业务数据，现在需要查材料了解详细盈利情况。
            工具: search_fulltext, args: {"query": "盈利", "topN": 5, "projectCode": "PRJ-2026-001"}
            观察: [{"materialTitle":"年报","snippet":"本年度净利润 3200 万元...","source":"PRJ-2026-001"}]
            
            思考: 信息已足够，组装最终答案。
            工具: FINAL_ANSWER, args: {"answer": "PRJ-2026-001 (新能源项目) 当前贷后阶段，剩余金额 3200 万元。据年报显示，本年度净利润 3200 万元。来源 [1]", "sources": [{"title":"年报","projectCode":"PRJ-2026-001","snippet":"本年度净利润 3200 万元..."}]}

            示例 2:
            用户: PRJ-2026-001 剩余金额多少?
            思考: 用户明确给出项目代码，直接查业务数据。
            工具: get_project_business_data, args: {"projectCode": "PRJ-2026-001"}
            观察: {"projectCode":"PRJ-2026-001","name":"新能源项目","stage":"贷后","amountWan":5000,"remainingAmountWan":3200}
            
            思考: 已获取精确数据，直接回答。
            工具: FINAL_ANSWER, args: {"answer": "PRJ-2026-001 剩余金额 3200 万元。", "sources": [{"title":"项目业务数据","projectCode":"PRJ-2026-001","field":"remainingAmountWan"}]}

            示例 3:
            用户: 今年结清了哪些项目?
            思考: 需查项目表，筛选状态=结清且创建时间在今年。
            工具: query_mysql, args: {"entity": "project", "filters": [{"field": "status", "operator": "=", "value": "结清"}, {"field": "createdAt", "operator": ">=", "value": "2026-01-01"}], "fields": ["code", "name", "createdAt"], "aggregate": "count"}
            观察: {"value": 3, "aggregate": "count"}
            
            思考: 有 3 个项目，查询详情。
            工具: query_mysql, args: {"entity": "project", "filters": [{"field": "status", "operator": "=", "value": "结清"}, {"field": "createdAt", "operator": ">=", "value": "2026-01-01"}], "fields": ["code", "name", "amountWan"]}
            观察: [{"code":"PRJ-2026-003","name":"项目A","amountWan":1000,"createdAt":"2026-03-15"},{"code":"PRJ-2026-007","name":"项目B","amountWan":500,"createdAt":"2026-05-20"},{"code":"PRJ-2026-012","name":"项目C","amountWan":2000,"createdAt":"2026-08-10"}]
            
            思考: 已获取详细列表，组装答案。
            工具: FINAL_ANSWER, args: {"answer": "今年共结清 3 个项目：PRJ-2026-003(项目A,1000万)、PRJ-2026-007(项目B,500万)、PRJ-2026-012(项目C,2000万)。", "sources": [{"title":"项目表查询","entity":"project","filters":"status=结清,createdAt>=2026-01-01"}]}""";
    }
}