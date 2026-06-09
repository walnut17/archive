package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import com.archive.provider.LLMProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmSummarizeToolTest {

    @Mock
    private LLMProvider llmProvider;

    @Mock
    private AgentContext ctx;

    @InjectMocks
    private LlmSummarizeTool tool;

    @Test
    void summarizeReturnsSummary() {
        String content = "这是一段很长的文本内容,包含了多个项目的详细信息。" .repeat(10);
        when(llmProvider.chat(anyString(), anyString())).thenReturn("摘要结果:这是浓缩后的内容。");

        var args = new LlmSummarizeTool.SummarizeArgs();
        args.content = content;
        ToolResult tr = tool.execute(args, ctx);

        assertTrue(tr.isOk());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) tr.getData();
        assertTrue(data.containsKey("summary"));
        assertEquals("摘要结果:这是浓缩后的内容。", data.get("summary"));
        assertTrue((Integer) data.get("originalLength") > 0);
    }

    @Test
    void summarizeWithCustomMaxLength() {
        String content = "非常长的文本内容需要被摘要成指定长度的结果。";
        when(llmProvider.chat(anyString(), anyString())).thenReturn("简短摘要。");

        var args = new LlmSummarizeTool.SummarizeArgs();
        args.content = content;
        args.maxLength = 100;
        ToolResult tr = tool.execute(args, ctx);

        assertTrue(tr.isOk());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) tr.getData();
        assertTrue(data.containsKey("summary"));
        assertEquals("简短摘要。", data.get("summary"));

        verify(llmProvider).chat(eq(""),
                contains("不超过100字"));
    }
}
