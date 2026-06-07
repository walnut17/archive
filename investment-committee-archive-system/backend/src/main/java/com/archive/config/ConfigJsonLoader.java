package com.archive.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * config.json 加载器.
 *
 * 启动时依次查找以下路径:
 *   1. ${CONFIG_JSON_PATH} 环境变量(WinSW 服务可设置)
 *   2. ./config/config.json(JAR 同级目录的 config/)
 *   3. ../config/config.json(上一级 config/,开发期常用)
 *
 * 找到后把 JSON 拍平为 key-value,作为高优先级 PropertySource 注入 Spring Environment.
 * 占位符 ${app.glm.api-key} 等可在 application.yml 里引用.
 *
 * @author Mavis
 */
@Slf4j
public class ConfigJsonLoader implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "configJson";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        Path configPath = resolveConfigPath();
        if (configPath == null) {
            log.info("config.json 未找到,使用 application.yml 默认值(开发环境)");
            return;
        }

        log.info("加载 config.json: {}", configPath.toAbsolutePath());

        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            // Strip UTF-8 BOM if present (Windows Notepad default UTF-8 saves with BOM)
            if (!json.isEmpty() && json.charAt(0) == '\uFEFF') {
                json = json.substring(1);
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            Map<String, Object> props = new HashMap<>();
            flatten(root, "", props);

            // 转换 key 形式:glm.apiKey → app.glm.api-key(Spring relaxed binding 友好)
            Map<String, Object> springProps = new HashMap<>();
            for (Map.Entry<String, Object> e : props.entrySet()) {
                String key = e.getKey();
                String[] parts = key.split("\\.");
                StringBuilder sb = new StringBuilder("app");
                for (String part : parts) {
                    sb.append('.').append(camelToKebab(part));
                }
                springProps.put(sb.toString(), e.getValue());
            }

            MapPropertySource source = new MapPropertySource(SOURCE_NAME, springProps);
            env.getPropertySources().addFirst(source);
            log.info("config.json 注入 {} 个属性", springProps.size());

        } catch (IOException e) {
            log.error("加载 config.json 失败: {}", configPath, e);
            throw new IllegalStateException("Failed to load config.json: " + configPath, e);
        }
    }

    private Path resolveConfigPath() {
        // 1. 环境变量
        String envPath = System.getenv("CONFIG_JSON_PATH");
        if (envPath != null && !envPath.isBlank()) {
            Path p = Paths.get(envPath);
            if (Files.exists(p)) {
                return p;
            }
            log.warn("CONFIG_JSON_PATH={} 不存在", envPath);
        }

        // 2. ./config/config.json
        Path p1 = Paths.get("config", "config.json");
        if (Files.exists(p1)) {
            return p1;
        }

        // 3. ../config/config.json
        Path p2 = Paths.get("..", "config", "config.json");
        if (Files.exists(p2)) {
            return p2;
        }

        return null;
    }

    private void flatten(JsonNode node, String prefix, Map<String, Object> result) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                // 跳过 _comment 注释字段
                if (key.startsWith("_")) {
                    continue;
                }
                flatten(field.getValue(), prefix + key + ".", result);
            }
        } else if (node.isArray()) {
            // 数组转 JSON 字符串
            result.put(prefix.substring(0, prefix.length() - 1), node.toString());
        } else if (node.isTextual()) {
            result.put(prefix.substring(0, prefix.length() - 1), node.asText());
        } else {
            result.put(prefix.substring(0, prefix.length() - 1), node.asText());
        }
    }

    private String camelToKebab(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    out.append('-');
                }
                out.append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
