package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class QueryMysqlToolTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AgentContext ctx;

    private QueryMysqlTool tool;

    private QueryMysqlTool newTool() {
        return new QueryMysqlTool(jdbcTemplate);
    }

    // ----------------------------------------------------------------
    // 1. Simple SELECT with WHERE
    // ----------------------------------------------------------------
    @Test
    void simpleSelectWithWhere() {
        tool = newTool();

        var args = new QueryMysqlTool.QueryMysqlArgs();
        args.setTable("project");
        args.setColumns(List.of("id", "name", "status"));

        var wc = new QueryMysqlTool.WhereCondition();
        wc.setColumn("status");
        wc.setOperator("=");
        wc.setValue("草稿");
        args.setWhere(List.of(wc));
        args.setLimit(10);

        var row = new HashMap<String, Object>();
        row.put("id", 1L);
        row.put("name", "测试项目");
        row.put("status", "草稿");
        var mockRows = List.of(row);

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(mockRows);

        var tr = tool.execute(args, ctx);
        assertTrue(tr.isOk());

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) tr.getData();
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) data.get("rows");
        assertEquals(1, rows.size());
        assertEquals("测试项目", rows.get(0).get("name"));

        var capturedSql = captureSql();
        assertTrue(capturedSql.contains("SELECT id, name, status FROM project"));
        assertTrue(capturedSql.contains("WHERE status = ?"));
        assertTrue(capturedSql.contains("LIMIT ?"));
    }

    // ----------------------------------------------------------------
    // 2. COUNT aggregation
    // ----------------------------------------------------------------
    @Test
    void countAggregation() {
        tool = newTool();

        var args = new QueryMysqlTool.QueryMysqlArgs();
        args.setTable("project");
        args.setAggregate("count");
        args.setAggregateColumn("*");

        var row = new HashMap<String, Object>();
        row.put("aggregate_value", 5L);
        var mockRows = List.of(row);

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(mockRows);

        var tr = tool.execute(args, ctx);
        assertTrue(tr.isOk());

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) tr.getData();
        assertEquals(5L, data.get("aggregate"));

        var capturedSql = captureSql();
        assertTrue(capturedSql.contains("SELECT COUNT(*) AS aggregate_value FROM project"));
    }

    // ----------------------------------------------------------------
    // 3. SUM aggregation
    // ----------------------------------------------------------------
    @Test
    void sumAggregation() {
        tool = newTool();

        var args = new QueryMysqlTool.QueryMysqlArgs();
        args.setTable("project");
        args.setAggregate("sum");
        args.setAggregateColumn("amount_wan");

        var row = new HashMap<String, Object>();
        row.put("aggregate_value", 10000L);
        var mockRows = List.of(row);

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(mockRows);

        var tr = tool.execute(args, ctx);
        assertTrue(tr.isOk());

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) tr.getData();
        assertEquals(10000L, data.get("aggregate"));

        var capturedSql = captureSql();
        assertTrue(capturedSql.contains("SELECT SUM(amount_wan) AS aggregate_value FROM project"));
    }

    // ----------------------------------------------------------------
    // 4. GROUP BY
    // ----------------------------------------------------------------
    @Test
    void groupByAggregation() {
        tool = newTool();

        var args = new QueryMysqlTool.QueryMysqlArgs();
        args.setTable("project");
        args.setAggregate("count");
        args.setAggregateColumn("id");
        args.setGroupBy("status");

        var row1 = new HashMap<String, Object>();
        row1.put("aggregate_value", 3L);
        row1.put("status", "草稿");
        var row2 = new HashMap<String, Object>();
        row2.put("aggregate_value", 2L);
        row2.put("status", "通过");
        var mockRows = List.of(row1, row2);

        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(mockRows);

        var tr = tool.execute(args, ctx);
        assertTrue(tr.isOk());

        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) tr.getData();
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) data.get("rows");
        assertEquals(2, rows.size());

        var capturedSql = captureSql();
        assertTrue(capturedSql.contains("GROUP BY status"));
    }

    // ----------------------------------------------------------------
    // 5. SQL injection attempt (should be rejected)
    // ----------------------------------------------------------------
    @Test
    void sqlInjectionAttemptRejected() {
        tool = newTool();

        var args = new QueryMysqlTool.QueryMysqlArgs();
        args.setTable("project");
        args.setColumns(List.of("id; DROP TABLE project"));

        var tr = tool.execute(args, ctx);
        assertFalse(tr.isOk());
        assertNotNull(tr.getError());
        assertTrue(tr.getError().contains("invalid characters"));

        // Never call JdbcTemplate for injection attempts
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    }

    // ----------------------------------------------------------------
    // 6. Unauthorized table (should be rejected)
    // ----------------------------------------------------------------
    @Test
    void unauthorizedTableRejected() {
        tool = newTool();

        var args = new QueryMysqlTool.QueryMysqlArgs();
        args.setTable("credit_card");

        var tr = tool.execute(args, ctx);
        assertFalse(tr.isOk());
        assertNotNull(tr.getError());
        assertTrue(tr.getError().contains("not in whitelist"));

        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    }

    // ----------------------------------------------------------------
    // 7. Unknown operator (should be rejected)
    // ----------------------------------------------------------------
    @Test
    void unknownOperatorRejected() {
        tool = newTool();

        var args = new QueryMysqlTool.QueryMysqlArgs();
        args.setTable("project");
        args.setColumns(List.of("id", "name"));

        var wc = new QueryMysqlTool.WhereCondition();
        wc.setColumn("status");
        wc.setOperator("BETWEEN");
        wc.setValue("a");
        args.setWhere(List.of(wc));

        var tr = tool.execute(args, ctx);
        assertFalse(tr.isOk());
        assertNotNull(tr.getError());
        assertTrue(tr.getError().contains("Unsupported operator"));

        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    }

    // ----------------------------------------------------------------
    // 8. Missing required fields (null table -> rejected)
    // ----------------------------------------------------------------
    @Test
    void missingTableRejected() {
        tool = newTool();

        var args = new QueryMysqlTool.QueryMysqlArgs();
        args.setTable(null);

        var tr = tool.execute(args, ctx);
        assertFalse(tr.isOk());
        assertNotNull(tr.getError());
        assertTrue(tr.getError().contains("not in whitelist"));

        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    }

    // ----------------------------------------------------------------
    // Helper: capture the SQL string from the last queryForList call
    // ----------------------------------------------------------------
    private String captureSql() {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(captor.capture(), any(Object[].class));
        return captor.getValue();
    }
}
