package com.archive.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 智谱 AI 配置(从 config.json 注入).
 *
 * @author Mavis
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.glm")
public class GlmProperties {
    private String apiKey;
    private String chatUrl;
    private String visionUrl;
    private String chatModel;
    private String visionModel;
    private Integer timeoutSeconds = 60;
}
