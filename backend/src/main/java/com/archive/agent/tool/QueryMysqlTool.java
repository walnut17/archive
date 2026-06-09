package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class QueryMysqlTool implements AgentTool {

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "project", "proposal", "material", "material_version",
            "todo", "user", "role", "dict_type", "dict_item"
    );

    private static final Map<String, Set<String>> ALLOWED_COLUMNS = buildAllowedColumns();

    private static final Set<String> ALLOWED_AGGREGATES = Set.of(
            "count", "sum", "avg", "max", "min", "group_by"
    );

    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "=", "!=", ">", ">=", "<", "<=", "in", "like", "is_null", "is_not_null"
    );

    private static final int MAX_ROWS = 500;
    private static final int MAX_IN_VALUES = 50;  // IN 长度上限,防注入/防 DoS

    private static Map<String, Set<String>> buildAllowedColumns() {
        Map<String, Set<String>> map = new HashMap<>();
        map.put("project", Set.of("id", "code", "name", "category", "owner_id", "amount_wan",
                "summary", "status", "scheduled_meeting_at", "remark",
                "created_at", "updated_at", "created_by", "updated_by"));
        map.put("proposal", Set.of("id", "code", "title", "project_id", "type", "summary",
                "status", "reviewed_at", "decision", "remark",
                "created_at", "updated_at", "created_by", "updated_by"));
        map.put("material", Set.of("id", "proposal_id", "title", "category",
                "current_version_id", "status", "description", "tags",
                "created_at", "updated_at", "created_by", "updated_by"));
        map.put("material_version", Set.of("id", "material_id", "version_no",
                "original_filename", "file_size", "mime_type", "sha256",
                "parse_status", "parsed_at", "parse_error",
                "uploaded_by", "change_note",
                "created_at", "updated_at", "created_by", "updated_by"));
        map.put("todo", Set.of("id", "title", "source", "source_ref_id", "project_id",
                "owner_id", "priority", "status", "due_at", "completed_at", "remark",
                "created_at", "updated_at", "created_by", "updated_by"));
        map.put("user", Set.of("id", "username", "display_name", "email", "role_id",
                "department", "status", "last_login_at",
                "created_at", "updated_at"));
        map.put("role", Set.of("id", "code", "name", "description",
                "created_at", "updated_at"));
        map.put("dict_type", Set.of("id", "type_code", "type_name", "description",
                "sort_order", "is_system", "enabled",
                "created_at", "updated_at", "created_by", "updated_by"));
        map.put("dict_item", Set.of("id", "type_code", "item_key", "item_value",
                "sort_order", "is_default", "enabled", "is_system", "remark",
                "created_at", "updated_at", "created_by", "updated_by"));
        return Collections.unmodifiableMap(map);
    }

    private final JdbcTemplate jdbcTemplate;

    @Override
    public String name() {
        return "query_mysql";
    }

    @Override
    public String description() {
        return "对授权表执行 SELECT 查询,支持聚合(COUNT/SUM/AVG/MAX/MIN)和 WHERE 条件过滤。只能查白名单表,不能 DDL/DML。";
    }

    @Override
    public Class<?> argsClass() {
        return QueryMysqlArgs.class;
    }

    @Override
    public ToolResult execute(Object args, AgentContext ctx) {
        QueryMysqlArgs queryArgs = (QueryMysqlArgs) args;

        // 1. Validate table
        var table = queryArgs.getTable();
        if (table == null || !ALLOWED_TABLES.contains(table)) {
            return ToolResult.error("Table '" + table + "' not in whitelist, allowed: " + ALLOWED_TABLES);
        }

        var allowedCols = ALLOWED_COLUMNS.get(table);

        // 2. Validate aggregate
        var aggregate = queryArgs.getAggregate();
        if (aggregate != null && !aggregate.isBlank()) {
            aggregate = aggregate.toLowerCase();
            if (!ALLOWED_AGGREGATES.contains(aggregate)) {
                return ToolResult.error("Unsupported aggregate function: " + aggregate);
            }
            // group_by 是非聚合函数, 独立处理: 需要 aggregateColumn 列表 (== GROUP BY 列)
            if ("group_by".equals(aggregate)) {
                var groupByCol = queryArgs.getAggregateColumn();
                if (groupByCol == null || groupByCol.isBlank()) {
                    return ToolResult.error("group_by requires aggregateColumn field");
                }
                if (!isValidColumnName(groupByCol)) {
                    return ToolResult.error("group_by column name '" + groupByCol + "' contains invalid characters");
                }
                if (!allowedCols.contains(groupByCol)) {
                    return ToolResult.error("Column '" + groupByCol + "' not allowed in table " + table);
                }
                // 设置 groupBy 让 SELECT 阶段使用
                queryArgs.setGroupBy(groupByCol);
            } else {
                var aggCol = queryArgs.getAggregateColumn();
                if (aggCol != null && !aggCol.isBlank()) {
                    if (!isValidColumnName(aggCol)) {
                        return ToolResult.error("Aggregate column name '" + aggCol + "' contains invalid characters");
                    }
                    if (!allowedCols.contains(aggCol)) {
                        return ToolResult.error("Column '" + aggCol + "' not allowed in table " + table);
                    }
                }
            }
        }

        // 3. Validate groupBy
        if (queryArgs.getGroupBy() != null && !queryArgs.getGroupBy().isBlank()) {
            if (!isValidColumnName(queryArgs.getGroupBy())) {
                return ToolResult.error("GROUP BY column name '" + queryArgs.getGroupBy() + "' contains invalid characters");
            }
            if (!allowedCols.contains(queryArgs.getGroupBy())) {
                return ToolResult.error("Column '" + queryArgs.getGroupBy() + "' not allowed in table " + table);
            }
        }

        // 4. Build SELECT columns
        var selectColumns = new ArrayList<String>();
        if ("group_by".equals(aggregate)) {
            // group_by 模式: SELECT groupByCol, COUNT(*) AS aggregate_value GROUP BY groupByCol
            selectColumns.add(queryArgs.getGroupBy());
            selectColumns.add("COUNT(*) AS aggregate_value");
        } else if (aggregate != null && !aggregate.isBlank()) {
            var aggCol = (queryArgs.getAggregateColumn() != null && !queryArgs.getAggregateColumn().isBlank())
                    ? queryArgs.getAggregateColumn() : "*";
            selectColumns.add(aggregate.toUpperCase() + "(" + aggCol + ") AS aggregate_value");
            if (queryArgs.getGroupBy() != null && !queryArgs.getGroupBy().isBlank()) {
                selectColumns.add(queryArgs.getGroupBy());
            }
        } else {
            if (queryArgs.getColumns() != null && !queryArgs.getColumns().isEmpty()) {
                for (var col : queryArgs.getColumns()) {
                    if (!isValidColumnName(col)) {
                        return ToolResult.error("Column name '" + col + "' contains invalid characters");
                    }
                    if (!allowedCols.contains(col)) {
                        return ToolResult.error("Column '" + col + "' not allowed in table " + table);
                    }
                }
                selectColumns.addAll(queryArgs.getColumns());
            } else {
                selectColumns.addAll(allowedCols);
            }
        }

        // 5. Build WHERE clause
        var params = new ArrayList<>();
        var whereClauses = new ArrayList<String>();

        if (queryArgs.getWhere() != null) {
            for (var wc : queryArgs.getWhere()) {
                if (!isValidColumnName(wc.getColumn())) {
                    return ToolResult.error("WHERE column name '" + wc.getColumn() + "' contains invalid characters");
                }
                if (!allowedCols.contains(wc.getColumn())) {
                    return ToolResult.error("Column '" + wc.getColumn() + "' not allowed in table " + table);
                }
                if (!ALLOWED_OPERATORS.contains(wc.getOperator())) {
                    return ToolResult.error("Unsupported operator: " + wc.getOperator() + ", allowed: " + ALLOWED_OPERATORS);
                }

                var col = wc.getColumn();
                var op = wc.getOperator();

                if ("is_null".equals(op)) {
                    whereClauses.add(col + " IS NULL");
                    // IS NULL 不占位 (LLM 也不会传 value)
                } else if ("is_not_null".equals(op)) {
                    whereClauses.add(col + " IS NOT NULL");
                } else if ("in".equals(op)) {
                    if (wc.getValue() instanceof List) {
                        var values = (List<?>) wc.getValue();
                        if (values.isEmpty()) {
                            return ToolResult.error("IN operator requires at least one value");
                        }
                        // 安全加固 1: IN 长度上限
                        if (values.size() > MAX_IN_VALUES) {
                            return ToolResult.error("IN operator values length " + values.size()
                                + " exceeds max " + MAX_IN_VALUES);
                        }
                        var placeholders = values.stream().map(v -> "?").collect(Collectors.joining(", "));
                        whereClauses.add(col + " IN (" + placeholders + ")");
                        params.addAll(values);
                    } else if (wc.getValue() != null) {
                        // 单值也走 IN, 安全起见按 IN 处理 (1 个值)
                        whereClauses.add(col + " IN (?)");
                        params.add(wc.getValue());
                    } else {
                        return ToolResult.error("IN operator requires a value");
                    }
                } else if ("like".equals(op)) {
                    if (wc.getValue() == null) {
                        return ToolResult.error("LIKE operator requires a value");
                    }
                    // 安全加固 2: LIKE 自动转义 % 和 _ (LLM 输出可能被恶意拼入)
                    String likeValue = escapeLikePattern(wc.getValue().toString());
                    whereClauses.add(col + " LIKE ?");
                    params.add(likeValue);
                } else {
                    if (wc.getValue() == null) {
                        return ToolResult.error("Operator '" + op + "' requires a value");
                    }
                    whereClauses.add(col + " " + op + " ?");
                    params.add(wc.getValue());
                }
            }
        }

        // 6. Build SQL
        var sql = new StringBuilder("SELECT ");
        sql.append(String.join(", ", selectColumns));
        sql.append(" FROM ").append(table);

        if (!whereClauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereClauses));
        }

        if (queryArgs.getGroupBy() != null && !queryArgs.getGroupBy().isBlank()) {
            sql.append(" GROUP BY ").append(queryArgs.getGroupBy());
        }

        if (queryArgs.getOrderBy() != null && !queryArgs.getOrderBy().isBlank()) {
            var sanitized = sanitizeOrderBy(queryArgs.getOrderBy());
            if (sanitized == null) {
                return ToolResult.error("Invalid ORDER BY format: " + queryArgs.getOrderBy());
            }
            sql.append(" ORDER BY ").append(sanitized);
        }

        var limit = (queryArgs.getLimit() != null && queryArgs.getLimit() > 0)
                ? Math.min(queryArgs.getLimit(), MAX_ROWS) : MAX_ROWS;
        sql.append(" LIMIT ?");
        params.add(limit);

        // 7. Execute
        try {
            var rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

            var result = new LinkedHashMap<String, Object>();
            result.put("rows", rows);
            result.put("rowCount", rows.size());
            if (aggregate != null && !aggregate.isBlank() && !rows.isEmpty()) {
                result.put("aggregate", rows.get(0).get("aggregate_value"));
            }
            return ToolResult.ok(result);

        } catch (Exception e) {
            return ToolResult.error("Query execution failed: " + e.getMessage());
        }
    }

    private boolean isValidColumnName(String name) {
        if (name == null || name.isEmpty()) return false;
        return name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }

    /**
     * 安全加固 2: LIKE 模式转义 % 和 _
     * - % 匹配任意字符串 -> 转义为 \%
     * - _ 匹配单字符 -> 转义为 \_
     * 让 LLM 输出的 "100%" 也能当字面值查, 不被当作通配符
     */
    private String escapeLikePattern(String value) {
        if (value == null) return null;
        // 顺序: 先转义 \, 再转义 % 和 _, 避免双重转义
        return value.replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
    }

    private String sanitizeOrderBy(String orderBy) {
        if (orderBy == null || orderBy.trim().isEmpty()) return null;
        var trimmed = orderBy.trim();
        if (!trimmed.matches("^[a-zA-Z_][a-zA-Z0-9_]*(\\s+(ASC|DESC))?$")) {
            return null;
        }
        return trimmed;
    }

    @Data
    public static class QueryMysqlArgs {
        private String table;
        private List<String> columns;
        private List<WhereCondition> where;
        private String aggregate;
        private String aggregateColumn;
        private String groupBy;
        private String orderBy;
        private Integer limit;
    }

    @Data
    public static class WhereCondition {
        private String column;
        private String operator;
        private Object value;
    }
}
