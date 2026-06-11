package com.archive.agent.tool.archive;

import com.archive.agent.AgentContext;
import com.archive.agent.tool.AgentTool;
import com.archive.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent 工具: archive_fs — 只读访问 D:/archive 材料目录.
 *
 * 支持 action: list / grep / read
 * 安全: 仅允许 files-root 和 parsed-root 白名单路径
 */
@Component
public class ArchiveFsTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ArchiveFsTool.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ArchiveFsActionRegistry registry;
    private final ArchivePathGuard guard;

    public ArchiveFsTool(ArchiveFsActionRegistry registry, ArchivePathGuard guard) {
        this.registry = registry;
        this.guard = guard;
    }

    @Override
    public String name() {
        return "archive_fs";
    }

    @Override
    public String description() {
        return "只读访问项目材料本地目录: list(ls), grep(搜文本), read(读文件)。"
                + "调用前须通过 find_project 锁定 projectCode。"
                + "zone: files(原始文件) / parsed(Tika 解析文本)。"
                + "优先用 materialVersionId 查 DB 路径，避免 LLM 猜测路径。";
    }

    @Override
    public Class<?> argsClass() {
        return Map.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Object args, AgentContext ctx) {
        Map<String, Object> argsMap;
        if (args instanceof Map) {
            argsMap = (Map<String, Object>) args;
        } else {
            return ToolResult.error("参数格式错误，需要 JSON Map");
        }

        String action = (String) argsMap.get("action");
        if (action == null) {
            return ToolResult.error("缺少必填字段 action，支持: " + registry.supportedActions());
        }

        String username = null;
        // 从 SecurityContext 获取 username 的逻辑（简化）
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) username = auth.getName();
        } catch (Exception ignored) {}

        ArchiveFsContext fsCtx = new ArchiveFsContext(guard, argsMap, username);

        try {
            ArchiveFsAction act = registry.getAction(action);
            ToolResult result = act.execute(fsCtx);
            log.info("[ArchiveFsTool] action={}, ok={}", action, result.isOk());
            return result;
        } catch (IllegalArgumentException e) {
            return ToolResult.error("未知 action: " + action + "，支持: " + registry.supportedActions());
        } catch (Exception e) {
            log.warn("[ArchiveFsTool] 执行异常: {}", e.getMessage(), e);
            return ToolResult.error("执行失败: " + e.getMessage());
        }
    }
}
