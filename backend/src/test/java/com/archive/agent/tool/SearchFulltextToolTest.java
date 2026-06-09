package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import com.archive.service.KnowledgeSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchFulltextToolTest {

    @Mock
    private KnowledgeSearchService searchService;

    @Mock
    private AgentContext ctx;

    @InjectMocks
    private SearchFulltextTool tool;

    @Test
    void normalSearchReturnsResults() {
        var result = KnowledgeSearchService.SearchResult.builder()
                .versionId(1L).materialId(1L).materialTitle("测试材料")
                .projectCode("PRJ-001").projectName("测试项目")
                .proposalCode("PRO-001").proposalTitle("测试议案")
                .snippet("匹配片段").fullText("完整文本").score(1.0)
                .build();
        when(searchService.search("test", 10)).thenReturn(List.of(result));
        when(ctx.getProjectCode()).thenReturn(null);

        var args = new SearchFulltextTool.SearchArgs();
        args.query = "test";
        ToolResult tr = tool.execute(args, ctx);

        assertTrue(tr.isOk());
        @SuppressWarnings("unchecked")
        List<KnowledgeSearchService.SearchResult> data = (List<KnowledgeSearchService.SearchResult>) tr.getData();
        assertEquals(1, data.size());
        assertEquals("测试材料", data.get(0).getMaterialTitle());
    }

    @Test
    void filterByProjectCode() {
        var r1 = KnowledgeSearchService.SearchResult.builder()
                .materialId(1L).projectCode("PRJ-001").build();
        var r2 = KnowledgeSearchService.SearchResult.builder()
                .materialId(2L).projectCode("PRJ-002").build();
        when(searchService.search("test", 10)).thenReturn(List.of(r1, r2));
        when(ctx.getProjectCode()).thenReturn("PRJ-001");

        var args = new SearchFulltextTool.SearchArgs();
        args.query = "test";
        ToolResult tr = tool.execute(args, ctx);

        assertTrue(tr.isOk());
        @SuppressWarnings("unchecked")
        List<KnowledgeSearchService.SearchResult> data = (List<KnowledgeSearchService.SearchResult>) tr.getData();
        assertEquals(1, data.size());
        assertEquals("PRJ-001", data.get(0).getProjectCode());
    }

    @Test
    void emptyQueryReturnsEmpty() {
        when(searchService.search("", 10)).thenReturn(List.of());
        when(ctx.getProjectCode()).thenReturn(null);

        var args = new SearchFulltextTool.SearchArgs();
        args.query = "";
        ToolResult tr = tool.execute(args, ctx);

        assertTrue(tr.isOk());
        @SuppressWarnings("unchecked")
        List<KnowledgeSearchService.SearchResult> data = (List<KnowledgeSearchService.SearchResult>) tr.getData();
        assertTrue(data.isEmpty());
    }
}
