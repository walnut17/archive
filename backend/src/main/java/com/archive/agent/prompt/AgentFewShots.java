package com.archive.agent.prompt;

import org.springframework.stereotype.Component;

/**
 * Agent few-shot 示例.
 * 为 LLM 提供 ReAct 循环的输入/输出示例.
 *
 * **Mavis 修 P0-19**: Sisyphus 原版 few-shot 用错字段名 (entity / filters, 实际是 table / where),
 * 修成跟 QueryMysqlTool 实际 QueryMysqlArgs 字段名一致.
 *
 * @author Mavis
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

            思考: 信息已足够，组装最终答案。
            工具: FINAL_ANSWER, args: {"answer": "PRJ-2026-001 (新能源项目) 当前贷后阶段，剩余金额 3200 万元。", "sources": [{"title":"项目业务数据","projectCode":"PRJ-2026-001","field":"remainingAmountWan"}]}

            示例 2 (query_mysql 表名 = table, 不是 entity):
            用户: 今年结清了哪些项目?
            思考: 需查 project 表，筛选 status=结清 且 今年创建。
            工具: query_mysql, args: {"table": "project", "where": [{"column": "status", "operator": "=", "value": "结清"}, {"column": "createdAt", "operator": ">=", "value": "2026-01-01"}], "columns": ["code", "name", "amountWan"]}
            观察: [{"code":"PRJ-2026-003","name":"项目A","amountWan":1000},{"code":"PRJ-2026-007","name":"项目B","amountWan":500},{"code":"PRJ-2026-012","name":"项目C","amountWan":2000}]

            思考: 已获取详细列表，组装答案。
            工具: FINAL_ANSWER, args: {"answer": "今年共结清 3 个项目：PRJ-2026-003(1000万)、PRJ-2026-007(500万)、PRJ-2026-012(2000万)。", "sources": [{"title":"project表查询","filters":"status=结清,createdAt>=2026-01-01"}]}

            示例 3 (query_mysql 聚合 + group_by):
            用户: 还没结清的项目有几个?按状态分组
            思考: 用 group_by 聚合, 按 status 分组统计
            工具: query_mysql, args: {"table": "project", "aggregate": "group_by", "aggregateColumn": "status", "where": [{"column": "status", "operator": "is_not_null", "value": ""}]}
            观察: [{"status":"草稿","aggregate_value":3},{"status":"审批中","aggregate_value":5},{"status":"贷后","aggregate_value":2}]

            工具: FINAL_ANSWER, args: {"answer": "未结清项目共 10 个：草稿 3 个、审批中 5 个、贷后 2 个。", "sources": [{"title":"项目表group_by状态"}]}

            示例 4 (operator 严格匹配):
            用户: 有哪些项目没有项目编号?
            思考: 用 is_null 查 code 为空的
            工具: query_mysql, args: {"table": "project", "columns": ["code", "name"], "where": [{"column": "code", "operator": "is_null", "value": ""}], "limit": 10}
            观察: []

            工具: FINAL_ANSWER, args: {"answer": "所有项目都有项目编号,没有缺失。", "sources": []}

            严格输出格式提醒:
            - 你的回复必须是合法 JSON, 不能有任何多余文字
            - 字段名严格: query_mysql 用 "table" (不是 entity), "where" (不是 filters), "column" (不是 field)
            - operator 严格: = / != / > / >= / < / <= / in / like / is_null / is_not_null
            - aggregate: count / sum / avg / max / min / group_by
            - 没信息就说"我不知道" + FINAL_ANSWER, 不要编造""";
    }
}
