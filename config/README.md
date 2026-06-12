# 配置文件说明

> 投委会档案管理系统 — 敏感配置(API key、密码、连接信息)不进 Git。

**项目协作**：根 [`README.md`](../README.md) · 环境依赖 [`docs/operations/ENVIRONMENT-DEPENDENCIES.md`](../docs/operations/ENVIRONMENT-DEPENDENCIES.md) · GLM 配置 [`docs/operations/GLM-KEY-SETUP.md`](../docs/operations/GLM-KEY-SETUP.md)

---

## 快速上手(3 步)

```bash
# 1. 复制模板为真实配置文件
cp config/config.example.json config/config.json

# 2. 编辑 config.json,填入真实值
#    (用你喜欢的编辑器,VSCode / Notepad++ 都行)
notepad config/config.json    # Windows
# 或者
code config/config.json      # VSCode

# 3. 启动后端 — 它会自动读 ./config/config.json
java -jar backend/archive.jar
```

---

## 配置文件位置(约定)

```
项目根目录/
├── config/
│   ├── config.example.json   ← 模板(进 Git)
│   ├── config.json           ← 真实配置(不进 Git,用户填)
│   └── README.md             ← 本文件
├── backend/
│   ├── archive.jar
│   └── logs/
└── frontend/
```

> 后端启动时,会在以下路径依次查找 config.json(找到就用):
> 1. `CONFIG_JSON_PATH` 环境变量
> 2. `D:/archive/config/config.json`（125 生产）
> 3. `./config/config.json`（JAR 同级）
> 4. `../config/config.json`（开发常用）
>
> **Python qa-agent** 使用相同顺序（见 `qa-agent/app/config_loader.py`），另加仓库根 `config/config.json` 便于从 `qa-agent/` 目录启动。

---

## qa-agent 共用配置

| 项 | 说明 |
|---|---|
| 文件 | 与 backend **同一份** `config/config.json` |
| 125 | `config.json` 放在 `D:\archive\config\`；WinSW 为 backend **与** qa-agent 均设置 `CONFIG_JSON_PATH=D:\archive\config\config.json` |
| 125 代码 | qa-agent 源码在 `D:\projects-online\qa-agent`（`.venv` 本地 `pip install`，不进 Git） |
| 专属段 | `qaAgent.host` / `qaAgent.port` / `qaAgent.maxIterations`（模板已加在 `config.example.json`） |
| 共用段 | `glm` · `database` · `storage` · `archive.networkDict` · `archive.queryMysql` |
| 不要 | 单独维护 `qa-agent/.env` 作为主配置（`.env.example` 仅文档化可选覆盖） |

---

## 后端代码如何读这个文件(Spring Boot)

```java
// 启动时加载 config.json,合并到 Spring Environment
@Component
public class ConfigJsonLoader implements EnvironmentPostProcessor {
    
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        Path configPath = Paths.get("config", "config.json");
        if (!Files.exists(configPath)) {
            // 不存在就用 application.yml 里的默认值(开发环境)
            log.warn("config.json not found at {}, using application.yml defaults", configPath);
            return;
        }
        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            
            // 拍平 JSON 到 properties(支持嵌套:gLm.apiKey → app.glm.api-key)
            Map<String, Object> props = new HashMap<>();
            flatten(root, "", props);
            
            // 把 config.json 的 key 映射到 Spring 占位符
            // 例:glm.apiKey → app.glm.api-key
            MapPropertySource source = new MapPropertySource("configJson", 
                props.entrySet().stream()
                    .collect(Collectors.toMap(
                        e -> e.getKey().replace('.', '-'),  // 兼容 Spring relaxed binding
                        Map.Entry::getValue
                    ))
            );
            env.getPropertySources().addFirst(source);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config.json", e);
        }
    }
    
    private void flatten(JsonNode node, String prefix, Map<String, Object> result) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> 
                flatten(e.getValue(), prefix + e.getKey() + ".", result));
        } else if (node.isTextual()) {
            result.put(prefix.substring(0, prefix.length() - 1), node.asText());
        } else {
            result.put(prefix.substring(0, prefix.length() - 1), node.asText());
        }
    }
}

// 业务代码用 @ConfigurationProperties 注入
@Data
@Component
@ConfigurationProperties(prefix = "app.glm")
public class GlmProperties {
    private String apiKey;
    private String chatUrl;
    private String visionUrl;
    private String chatModel;
    private String visionModel;
}

@Data
@Component
@ConfigurationProperties(prefix = "app.database")
public class DatabaseProperties {
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private String params;
}
```

**application.yml 里用占位符:**

```yaml
spring:
  datasource:
    url: jdbc:mysql://${app.database.host}:${app.database.port}/${app.database.database}?${app.database.params}
    username: ${app.database.username}
    password: ${app.database.password}

app:
  glm:
    api-key: ${glm.apiKey}
    chat-url: ${glm.chatUrl}
    # ...
```

---

## .gitignore 配置(强烈建议)

在 `projects-online` 仓库根目录的 `.gitignore` 加:

```gitignore
# 投委会档案 — 本地配置(不进 Git)
config/config.json
!config/config.example.json
```

---

## 安全提醒 ⚠️

| 风险 | 缓解 |
|---|---|
| 把 config.json 误提交到 Git | 1) 加进 .gitignore;2) 配合 `git-secrets` hook 扫描;3) PR 时人工 review |
| 智谱 API key 泄露 | 1) 定期去智谱后台 rotate key;2) 后端日志打码(已实现);3) 设置 IP 白名单(智谱控制台) |
| MySQL 密码泄露 | 1) 用专用账号 `archive_app`(只对 archive_db 有权限);2) 不用 root;3) 定期改密码 |
| 配置文件被其他人看到 | 1) Windows NTFS 权限:`icacls config.json /inheritance:r /grant:r "YOURNAME:(R,W)"`;2) 后端服务用专用账号运行(非管理员);3) 部署到独立机器,不开放远程桌面 |

---

## 完整字段说明(参见 config.example.json 的 `_comment`)

每个字段都有 `_comment` 注释说明用途。模板已经覆盖:
- 智谱 AI(API key + URL + 模型)
- MySQL(主账号 + 备份账号)
- 邮件 SMTP
- 存储路径
- 备份配置
- 服务端口
- 脱敏配置
- 监控告警

**最少必填项**才能启动:
- `glm.apiKey`
- `database.host` / `database.port` / `database.database` / `database.username` / `database.password`
- `storage.fileRoot`(目录存在)

其他都是可选的,有默认值或可后续开启。

---

## 常见问题

**Q: 启动报 "config.json not found"?**
A: 把 config.json 放在 `./config/config.json`(相对 JAR 所在目录)。WinSW 服务的"工作目录"配置项会决定这个相对路径。

**Q: 改了 config.json 不生效?**
A: 重启后端服务即可。Spring Boot 的 `EnvironmentPostProcessor` 只在启动时执行。

**Q: 怎么在开发环境(IDE)用不同的配置?**
A: 在 IDE 启动配置里加环境变量 `CONFIG_JSON_PATH=D:/dev-config.json`,指向你的开发用配置。

**Q: 我能不能用 YAML 不用 JSON?**
A: 模板用的是 JSON(`.json`),因为结构和 application.yml 区分开比较清楚。如果你想统一用 YAML,改 `ConfigJsonLoader` 加载 `application-local.yml` 也行,自己定。

---

*本配置机制是 Mavis 设计,目标:让敏感信息跟代码完全解耦,代码进 Git,配置留在本地。*
