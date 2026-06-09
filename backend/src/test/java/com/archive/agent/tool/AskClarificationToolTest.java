package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AskClarificationToolTest {

    @Mock
    private AgentContext ctx;

    private final AskClarificationTool tool = new AskClarificationTool();

    @Test
    void clarificationReturnsQuestion() {
        var args = new AskClarificationTool.ClarificationArgs();
        args.question = "您想查询哪个项目的详情？";
        ToolResult tr = tool.execute(args, ctx);

        assertTrue(tr.isOk());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) tr.getData();
        assertEquals("您想查询哪个项目的详情？", data.get("question"));
        assertNotNull(data.get("options"));
        assertTrue(((List<?>) data.get("options")).isEmpty());
    }

    @Test
    void clarificationWithOptions() {
        var args = new AskClarificationTool.ClarificationArgs();
        args.question = "您想按哪个维度查看？";
        args.options = List.of("按项目", "按时间", "按金额");
        ToolResult tr = tool.execute(args, ctx);

        assertTrue(tr.isOk());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) tr.getData();
        assertEquals("您想按哪个维度查看？", data.get("question"));
        assertEquals(3, ((List<?>) data.get("options")).size());
        assertEquals("按项目", ((List<?>) data.get("options")).get(0));
    }
}
