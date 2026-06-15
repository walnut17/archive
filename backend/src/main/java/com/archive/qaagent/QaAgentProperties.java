package com.archive.qaagent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.qa-agent")
public class QaAgentProperties {

    /** 启用 Python qa-agent 微服务（替代 Java AgentEngine）. */
    private boolean enabled = true;

    private String baseUrl = "http://127.0.0.1:8001";

    private int timeoutSeconds = 120;

    /** 分析入队开关 (cutover J2). */
    private AnalysisEnqueue analysisEnqueue = new AnalysisEnqueue();

    @Data
    public static class AnalysisEnqueue {
        /** 是否在解析完成后自动入队后台分析. */
        private boolean enabled = true;
        /** 入队触发原因（日志/审计用）. */
        private String reason = "parse_complete";
    }
}
