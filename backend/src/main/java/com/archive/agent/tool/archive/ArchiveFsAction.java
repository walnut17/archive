package com.archive.agent.tool.archive;

import com.archive.agent.tool.ToolResult;

/**
 * ArchiveFs 动作接口（策略模式）.
 * 实现类通过 @Component 自动注册到 ArchiveFsActionRegistry.
 * 未来可扩展: ReadPdfPageAction, ReadImageAction 等.
 */
public interface ArchiveFsAction {

    /** 动作名, 与 LLM 调用的 args.action 对应. */
    String actionName();

    /** 执行动作. */
    ToolResult execute(ArchiveFsContext ctx);
}
