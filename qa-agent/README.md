# QA Agent (Python FastAPI)

投委会档案智能问答 / LLM 抽取微服务。Java Spring Boot 通过 HTTP 调用。

## 快速启动

```powershell
cd qa-agent
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
copy .env.example .env
# 编辑 .env：GLM_API_KEY、MYSQL_*

.\.venv\Scripts\uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload
```

健康检查：`GET http://127.0.0.1:8001/health`

## Java 侧配置

`application.yml` / `config.json`：

```yaml
app:
  qa-agent:
    enabled: true
    base-url: http://127.0.0.1:8001
spring:
  ai:
    agent:
      enabled: false
```

## 端点

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 健康检查 |
| POST | `/v1/ask` | 单轮问答 |
| POST | `/v1/turn/{session_id}` | 多轮问答 |
| POST | `/v1/extract/project-fields` | 立项字段抽取 |

## 测试

### 离线（mock GLM/DB，无需启动服务）

```powershell
.\.venv\Scripts\pytest tests/ -q -m "not live"
```

### 直连 qa-agent 后台（需先启动 uvicorn）

```powershell
# 终端 1
.\.venv\Scripts\uvicorn app.main:app --host 127.0.0.1 --port 8001

# 终端 2 — 任选其一
.\scripts\run_http_tests.ps1
# 或
$env:QA_AGENT_BASE_URL = "http://127.0.0.1:8001"
.\.venv\Scripts\pytest tests/test_api_http_live.py -q
.\.venv\Scripts\python scripts/smoke_http.py
```

环境变量：

| 变量 | 默认 | 说明 |
|---|---|---|
| `QA_AGENT_BASE_URL` | `http://127.0.0.1:8001` | live / smoke 测试目标 |
| `QA_AGENT_HTTP_TIMEOUT` | `120` | HTTP 超时秒数 |

自动化案例文档：[`test_task/AT-001-qa-agent-http-smoke.md`](../test_task/AT-001-qa-agent-http-smoke.md)

架构说明：[`docs/architecture/08-qa-agent-python-service.md`](../docs/architecture/08-qa-agent-python-service.md)
