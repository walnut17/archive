# QA Agent (Python FastAPI)

投委会档案智能问答 / LLM 抽取微服务。**与 Java 后端共用同一份 `config.json`**。

## 配置（与主项目一致）

| 优先级 | 来源 |
|---|---|
| 1 | **`config/config.json`**（与 Spring Boot 同文件） |
| 2 | 环境变量 `CONFIG_JSON_PATH`（指定 json 路径，125 与 backend 相同） |
| 3 | 可选 env 覆盖：`GLM_API_KEY`、`MYSQL_PASSWORD` 等（见 `.env.example`） |

**查找顺序**（与 `ConfigJsonLoader.java` 一致）：

1. `$CONFIG_JSON_PATH`
2. `D:/archive/config/config.json`（125 生产）
3. `./config/config.json`
4. `../config/config.json`
5. 仓库 `config/config.json`（从 `qa-agent/` 开发时）

**从 config.json 读取的字段**：

| JSON 路径 | qa-agent 用途 |
|---|---|
| `glm.*` | 智谱 LLM |
| `database.*` | MySQL |
| `storage.fileRoot` / `parsedRoot` | archive_fs（二期） |
| `archive.networkDict.*` | network_dict_lookup（二期） |
| `archive.queryMysql.*` | query_mysql 上限 |
| `qaAgent.host` / `port` / `maxIterations` | 本服务监听与 ReAct 步数 |

模板见 [`config/config.example.json`](../config/config.example.json) 末尾 **`qaAgent`** 段。复制为 `config/config.json` 后 **Java + qa-agent 共用**，无需单独 `.env`。

## 快速启动

```powershell
# 1. 确保主项目 config 已就绪（与 backend 相同一步）
copy config\config.example.json config\config.json
# 编辑 config\config.json：glm.apiKey、database.password 等

# 2. 启动 qa-agent
cd qa-agent
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

**125 生产（当前：手工启动）**：

- 代码：`D:\projects-online\qa-agent`（`git pull` → 本地建 `.venv`）
- 配置：`D:\archive\config\config.json`（启动脚本设 `CONFIG_JSON_PATH`）
- 启动：`.\deploy\scripts\start-qa-agent.ps1`（前台，见 [`RUNBOOK.md`](../docs/operations/RUNBOOK.md) §1.2）
- **后续**：WinSW / 与 backend 一体化部署（`deploy/winsw/qa-agent.xml` 已预留，暂不必装）

健康检查：`GET /health` → 含 `config_json` 路径，便于确认读到的文件。

## Java 侧

`application.yml` 从同一 config 读 `qaAgent.port` 拼 `app.qa-agent.base-url`：

```yaml
app:
  qa-agent:
    enabled: true
    base-url: http://${app.qa-agent.host:127.0.0.1}:${app.qa-agent.port:8001}
spring:
  ai:
    agent:
      enabled: false
```

## 端点

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 健康检查 + config 路径 |
| POST | `/v1/ask` | 单轮问答 |
| POST | `/v1/turn/{session_id}` | 多轮问答 |
| POST | `/v1/extract/project-fields` | 立项字段抽取 |

## 测试

```powershell
.\.venv\Scripts\pytest tests/ -q
```

架构说明：[`docs/architecture/08-qa-agent-python-service.md`](../docs/architecture/08-qa-agent-python-service.md) · 库表：[`docs/architecture/DATABASE.md`](../docs/architecture/DATABASE.md)
