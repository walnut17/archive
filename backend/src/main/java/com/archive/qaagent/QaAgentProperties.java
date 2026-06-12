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
}
