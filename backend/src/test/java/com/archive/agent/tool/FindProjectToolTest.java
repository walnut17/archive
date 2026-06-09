package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import com.archive.entity.Project;
import com.archive.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FindProjectToolTest {

    @Mock
    private ProjectRepository projectRepo;

    @Mock
    private AgentContext ctx;

    @InjectMocks
    private FindProjectTool tool;

    @Test
    void exactMatchByCode() {
        // 1. Exact match by project code
        Project project = Project.builder()
                .code("PRJ-2026-001")
                .name("新能源项目")
                .customerName("宁德时代")
                .build();
        when(projectRepo.findByCode("PRJ-2026-001")).thenReturn(Optional.of(project));

        var args = new FindProjectTool.FindProjectArgs();
        args.query = "PRJ-2026-001";
        ToolResult tr = tool.execute(args, ctx);

        assertTrue(tr.isOk());
        verify(ctx).setProjectCode("PRJ-2026-001");
        @SuppressWarnings("unchecked")
        List<FindProjectTool.FindProjectMatch> data = (List<FindProjectTool.FindProjectMatch>) tr.getData();
        assertEquals(1, data.size());
        assertEquals("PRJ-2026-001", data.get(0).getProjectCode());
        assertEquals("新能源项目", data.get(0).getProjectName());
        assertEquals(1.0, data.get(0).getConfidence());
    }

    @Test
    void fuzzyMatchReturnsResults() {
        // 2. Fuzzy FULLTEXT match finds results
        when(projectRepo.findByCode("新能源")).thenReturn(Optional.empty());
        Object[] row1 = new Object[]{"PRJ-2026-001", "新能源项目", "宁德时代", 8.5};
        Object[] row2 = new Object[]{"PRJ-2026-002", "新能源二期", "比亚迪", 5.1};
        when(projectRepo.searchByNameOrCustomerFulltext("新能源", 5)).thenReturn(List.of(row1, row2));

        var args = new FindProjectTool.FindProjectArgs();
        args.query = "新能源";
        ToolResult tr = tool.execute(args, ctx);

        assertTrue(tr.isOk());
        // confidence >= 0.7 for top match, should lock project
        verify(ctx).setProjectCode("PRJ-2026-001");
        @SuppressWarnings("unchecked")
        List<FindProjectTool.FindProjectMatch> data = (List<FindProjectTool.FindProjectMatch>) tr.getData();
        assertEquals(2, data.size());
        assertEquals("PRJ-2026-001", data.get(0).getProjectCode());
        assertEquals(1.0, data.get(0).getConfidence()); // max score = 8.5, confidence = 8.5/8.5
        assertEquals("PRJ-2026-002", data.get(1).getProjectCode());
        assertEquals(5.1 / 8.5, data.get(1).getConfidence(), 0.001);
    }

    @Test
    void noMatchReturnsEmpty() {
        // 3. No match found returns empty
        when(projectRepo.findByCode("XYZ-999")).thenReturn(Optional.empty());
        when(projectRepo.searchByNameOrCustomerFulltext("XYZ-999", 5)).thenReturn(List.of());

        var args = new FindProjectTool.FindProjectArgs();
        args.query = "XYZ-999";
        ToolResult tr = tool.execute(args, ctx);

        assertTrue(tr.isOk());
        verify(ctx, never()).setProjectCode(any());
        @SuppressWarnings("unchecked")
        List<FindProjectTool.FindProjectMatch> data = (List<FindProjectTool.FindProjectMatch>) tr.getData();
        assertTrue(data.isEmpty());
    }
}
