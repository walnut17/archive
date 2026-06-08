package com.archive.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据库配置(从 config.json 注入).
 *
 * @author Mavis
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.database")
public class DatabaseProperties {
    private String host = "localhost";
    private Integer port = 3306;
    private String database = "archive_db";
    private String username = "archive_app";
    private String password = "";
    private String params = "useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    private Integer poolMaxSize = 10;
    private Integer poolMinIdle = 2;
}
