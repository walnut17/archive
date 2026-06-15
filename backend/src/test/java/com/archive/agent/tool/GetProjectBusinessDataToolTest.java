package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import com.archive.entity.Project;
import com.archive.repository.MaterialRepository;
import com.archive.repository.ProjectRepository;
import com.archive.repository.ProposalRepository;
import com.archive.repository.TodoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetProjectBusinessDataToolTest {

    @Mock
    private ProjectRepository projectRepo;

    @Mock
    private TodoRepository todoRepo;

    @Mock
    private MaterialRepository materialRepo;

    @Mock
    private ProposalRepository proposalRepo;

    @Mock
    private AgentContext ctx;

    @InjectMocks
    private GetProjectBusinessDataTool tool;

    @Test
    void getBusinessDataForExistingProject() {
        Project project = Project.builder()
                .id(1L)
                .code("PRJ-2026-001")
                .name("新能源项目")
                .status("审议中")
                .amountWan(5000L)
                .category("股权类")
                .customerName("宁德时代")
                .build();
        when(projectRepo.findByCode("PRJ-2026-001")).thenReturn(Optional.of(project));
        when(todoRepo.countByProjectIdAndStatus(1L, "pending")).thenReturn(3L);
        when(materialRepo.countByProjectId(1L)).thenReturn(12L);
        when(proposalRepo.countCommitteeByProjectId(1L)).thenReturn(2L);
        when(proposalRepo.countMaintenanceByProjectId(1L)).thenReturn(1L);

        var args = new GetProjectBusinessDataTool.GetBusinessDataArgs();
        args.projectCode = "PRJ-2026-001";
        ToolResult tr = tool.execute(args, ctx);

        assertTrue(tr.isOk());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) tr.getData();
        assertEquals("PRJ-2026-001", data.get("projectCode"));
        assertEquals("新能源项目", data.get("projectName"));
        assertEquals("审议中", data.get("status"));
        assertEquals(5000L, data.get("amountWan"));
        assertEquals("股权类", data.get("category"));
        assertEquals("宁德时代", data.get("customerName"));
        assertEquals(3L, data.get("todoCount"));
        assertEquals(12L, data.get("materialCount"));
        assertEquals(2L, data.get("committeeProposalCount"));
        assertEquals(1L, data.get("maintenanceBundleCount"));
        assertEquals(2L, data.get("proposalCount"));
    }
}
