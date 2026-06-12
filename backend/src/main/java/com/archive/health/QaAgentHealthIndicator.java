package com.archive.health;

import com.archive.qaagent.QaAgentClient;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Actuator HealthIndicator — 检测 Python qa-agent 微服务状态.
 * 通过调用 qa-agent 的 GET /health 判断.
 */
@Component
@ConditionalOnProperty(name = "app.qa-agent.enabled", havingValue = "true", matchIfMissing = true)
public class QaAgentHealthIndicator extends AbstractHealthIndicator {

    private final QaAgentClient qaAgentClient;

    public QaAgentHealthIndicator(QaAgentClient qaAgentClient) {
        this.qaAgentClient = qaAgentClient;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            boolean healthy = qaAgentClient.isHealthy();
            if (healthy) {
                builder.up().withDetail("qa-agent", "reachable");
            } else {
                builder.down().withDetail("qa-agent", "unreachable");
            }
        } catch (Exception e) {
            builder.down().withDetail("qa-agent", e.getMessage());
        }
    }
}
