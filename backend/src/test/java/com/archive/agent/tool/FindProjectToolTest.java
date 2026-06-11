package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import com.archive.entity.Project;
import com.archive.repository.ProjectRepository;
import com.archive.service.GlmService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FindProjectToolTest {

    @Mock
    private ProjectRepository projectRepo;

    @Mock
    private GlmService glmService;

    @Mock
    private AgentContext ctx;

    @InjectMocks
    private FindProjectTool tool;

    @Test
    void buildSearchVariants_skipsTokenSplitForProjectCode() {
        assertEquals(List.of("PRJ-2026-001"), FindProjectTool.buildSearchVariants("PRJ-2026-001"));
    }

    @Test
    void buildSearchVariants_stripsProjectSuffixAndExtractsLatinToken() {
        List<String> variants = FindProjectTool.buildSearchVariants("lmz项目");
        assertEquals(List.of("lmz项目", "lmz"), variants);
    }

    @Test
    void exactMatchByCode() {
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
    }

    @Test
    void fuzzyMatchReturnsResults() {
        when(projectRepo.findByCode("新能源")).thenReturn(Optional.empty());
        Object[] row1 = new Object[]{"PRJ-2026-001", "新能源项目", "宁德时代", 8.5};
        Object[] row2 = new Object[]{"PRJ-2026-002", "新能源二期", "比亚迪", 5.1};
        when(projectRepo.searchByNameOrCustomerFulltext("新能源", 5)).thenReturn(List.of(row1, row2));

        var args = new FindProjectTool.FindProjectArgs();
        args.query = "新能源";
        ToolResult tr = tool.execute(args, ctx);

        assertTrue(tr.isOk());
        verify(ctx).setProjectCode("PRJ-2026-001");
        @SuppressWarnings("unchecked")
        List<FindProjectTool.FindProjectMatch> data = (List<FindProjectTool.FindProjectMatch>) tr.getData();
        assertEquals(2, data.size());
    }

    @Test
    void likeFallbackUsesStrippedVariant() {
        when(projectRepo.findByCode(anyString())).thenReturn(Optional.empty());
        when(projectRepo.searchByNameOrCustomerFulltext(anyString(), eq(5))).thenReturn(List.of());
        when(projectRepo.searchByKeywordAsList(eq("lmz项目"), any(Pageable.class))).thenReturn(List.of());
        Project lmz = Project.builder()
                .code("PRJ-LMZ-001")
                .name("林谋志借款项目")
                .customerName("林谋志")
                .build();
        when(projectRepo.searchByKeywordAsList(eq("lmz"), any(Pageable.class))).thenReturn(List.of(lmz));

        var args = new FindProjectTool.FindProjectArgs();
        args.query = "lmz项目";
        args.topN = 5;
        ToolResult tr = tool.execute(args, ctx);

        assertTrue(tr.isOk());
        verify(ctx).setProjectCode("PRJ-LMZ-001");
        @SuppressWarnings("unchecked")
        List<FindProjectTool.FindProjectMatch> data = (List<FindProjectTool.FindProjectMatch>) tr.getData();
        assertEquals(1, data.size());
        assertEquals("PRJ-LMZ-001", data.get(0).getProjectCode());
        verify(glmService, never()).semanticMatchProjects(anyString(), anyList(), anyInt());
    }

    @Test
    void noMatchReturnsEmpty() {
        when(projectRepo.findByCode("XYZ-999")).thenReturn(Optional.empty());
        when(projectRepo.searchByNameOrCustomerFulltext(anyString(), anyInt())).thenReturn(List.of());
        when(projectRepo.searchByKeywordAsList(anyString(), any(Pageable.class))).thenReturn(List.of());
        when(projectRepo.count()).thenReturn(100L);
        when(projectRepo.findAll()).thenReturn(List.of());
        when(glmService.semanticMatchProjects(eq("XYZ-999"), anyList(), anyInt())).thenReturn(List.of());

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
