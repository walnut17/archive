package com.archive.agent.tool;

import com.archive.agent.AgentContext;

public interface AgentTool {
    String name();
    String description();
    Class<?> argsClass();
    ToolResult execute(Object args, AgentContext ctx);
}
