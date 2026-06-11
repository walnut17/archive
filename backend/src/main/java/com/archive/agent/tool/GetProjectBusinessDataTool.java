package com.archive.agent.tool;

import com.archive.agent.AgentContext;
import com.archive.entity.Project;
import com.archive.repository.MaterialRepository;
import com.archive.repository.ProjectRepository;
import com.archive.repository.TodoRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetProjectBusinessDataTool implements AgentTool {

    private final ProjectRepository projectRepo;
    private final TodoRepository todoRepo;
    private final MaterialRepository materialRepo;

    @Override
    public String name() {
        return "get_project_business_data";
    }

    @Override
    public String description() {
        return "获取项目的聚合业务数据(金额、状态、待办数、材料份数等)。需先通过 find_project 锁定 projectCode。";
    }

    @Override
    public Class<?> argsClass() {
        return GetBusinessDataArgs.class;
    }

    @Override
    public ToolResult execute(Object argsObj, AgentContext ctx) {
        GetBusinessDataArgs args = (GetBusinessDataArgs) argsObj;
        String projectCode = args.projectCode != null ? args.projectCode : ctx.getProjectCode();
        if (projectCode == null || projectCode.isBlank()) {
            return ToolResult.error("projectCode is required");
        }
        var projectOpt = projectRepo.findByCode(projectCode);
        if (projectOpt.isEmpty()) {
            return ToolResult.error("Project not found: " + projectCode);
        }
        Project project = projectOpt.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectCode", project.getCode());
        data.put("projectName", project.getName());
        data.put("status", project.getStatus());
        data.put("amountWan", project.getAmountWan());
        data.put("category", project.getCategory());
        data.put("customerName", project.getCustomerName());
        data.put("todoCount", todoRepo.countByProjectIdAndStatus(project.getId(), "pending"));
        data.put("materialCount", materialRepo.countByProjectId(project.getId()));
        return ToolResult.ok(data);
    }

    @Data
    public static class GetBusinessDataArgs {
        @JsonProperty("projectCode") String projectCode;
    }
}
