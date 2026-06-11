package com.archive.agent.tool.archive;

import java.util.Map;

/**
 * ArchiveFs 工具调用上下文.
 */
public class ArchiveFsContext {

    private final ArchivePathGuard guard;
    private final String action;
    private final String zone;
    private final String relativePath;
    private final String pattern;
    private final int maxLines;
    private final int maxBytes;
    private final String username;

    public ArchiveFsContext(ArchivePathGuard guard, Map<String, Object> args, String username) {
        this.guard = guard;
        this.action = (String) args.get("action");
        this.zone = (String) args.getOrDefault("zone", "parsed");
        this.relativePath = (String) args.get("relativePath");
        this.pattern = (String) args.get("pattern");
        this.maxLines = args.containsKey("maxLines") ? ((Number) args.get("maxLines")).intValue() : 100;
        this.maxBytes = args.containsKey("maxBytes") ? ((Number) args.get("maxBytes")).intValue() : 262144;
        this.username = username;
    }

    public ArchivePathGuard guard() { return guard; }
    public String action() { return action; }
    public String zone() { return zone; }
    public String relativePath() { return relativePath; }
    public String pattern() { return pattern; }
    public int maxLines() { return Math.min(maxLines, 200); }
    public int maxBytes() { return Math.min(maxBytes, 524288); }
    public String username() { return username; }
}
