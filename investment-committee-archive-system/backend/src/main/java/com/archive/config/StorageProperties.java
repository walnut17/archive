package com.archive.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 存储配置(从 config.json 注入).
 *
 * @author Mavis
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {
    private String fileRoot = "D:/archive/files";
    private String parsedRoot = "D:/archive/parsed";
    private String logRoot = "D:/archive/logs";
    private Integer uploadMaxSizeMB = 100;
}
