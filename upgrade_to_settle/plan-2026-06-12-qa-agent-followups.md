# plan-2026-06-12-qa-agent-followups — qa-agent 收尾（配置/测试/工具补全）

> **状态**：`OPEN` — 自 [`plan-2026-06-12-qa-python-upload-first`](plan-2026-06-12-qa-python-upload-first.md) 关单后拆出  
> **主 plan 已交付**：Python qa-agent MVP+8 工具 · Java BFF · v1.1 F/G · WinSW · RI-16 上传

---

## 0. Case 元信息

| 字段 | 内容 |
|---|---|
| **路由 ID** | `plan-2026-06-12-qa-agent-followups` |
| **类型** | `UPGRADE` |
| **Case 状态** | `OPEN` |
| **标题** | qa-agent 配置统一 · archive_fs 补全 · Java 测试迁移 · v1.1 单测 |
| **需求锚点** | 父 plan §1.2 E/I/J 遗留 · [`08-qa-agent-python-service.md`](../docs/architecture/08-qa-agent-python-service.md) · [`07-archive-fs-agent-tools.md`](../docs/architecture/07-archive-fs-agent-tools.md) |
| **父 plan** | [`done/plan-2026-06-12-qa-python-upload-first.md`](done/plan-2026-06-12-qa-python-upload-first.md)（CLOSED @ `847b813`） |

---

## 1. 任务描述（PM / Reviewer 拆分，非 agent-block）

### 做

| # | 项 | 验收 |
|---|---|---|
| F-01 | **config.json 统一** | Python `config_loader.py` 与 Java `ConfigJsonLoader` 同序读 `config.json`；`qaAgent` 段；Java `app.qa-agent.base-url` 从 host/port 拼装；`GET /health` 回显 `config_json` 路径 |
| F-02 | **`archive_fs` materialVersionId** | `materialVersionId` → SQL 查 `storage_path` / `parsed_text_path` 再 `list/grep/read`（对齐父 plan §2.2） |
| F-03 | **v1.1 单测** | `find_project._fmt` 无锁/有锁各 decision 分支表；至少 1 个 turn/ask 断言 `agent_sources` 非空 |
| F-04 | **Java 测试迁移（§1.2 I/J）** | `AgentIntegrationTest` 改 mock Python 或 `@MockBean QaAgentClient`；可选 `QaAgentClientTest`；`08` §7 补「Java Agent 已退役」 |
| F-05 | **QaAgentClient 小修** | 去掉重复 `import Map`；`isHealthy()` 使用 `timeoutSeconds` |

### 不做

- §1.4 125 全链路 Co-test → **AT-001**（Operator / Auto-test，非本 plan）
- `Knowledge.vue` clarification UI（可选，另开或 WONTFIX）
- `registry.dispatch_tool` 条件锁定与 Java 完全对齐（现行为 Reviewer 已接受）

### 验收

- [ ] F-01～F-05 勾选
- [ ] `pytest` 全绿（含新增 v1.1 单测）
- [ ] `mvn test` 不因 Agent 迁移挂（本机或 CI）

---

## 2. 开发说明（架构师 · Coder 工单）

| 路径 | 说明 |
|---|---|
| `qa-agent/app/config_loader.py` | 新增/完善（工作区或有 WIP） |
| `qa-agent/app/config.py` | 从 config.json load，env 可选覆盖 |
| `qa-agent/app/agent/tools/archive_fs.py` | materialVersionId 绑路径 |
| `qa-agent/tests/test_find_project_v11.py` | 新建 — decision 分支 |
| `backend/.../QaAgentClient.java` | import 卫生 + health timeout |
| `backend/src/test/.../AgentIntegrationTest.java` | mock Python |
| `config/config.example.json` | `qaAgent` 段（若 F-01 未合入） |
| `docs/architecture/08-qa-agent-python-service.md` | §7 退役说明 |

**推荐顺序**：F-01 config 统一 → F-02 archive_fs → F-03 单测 → F-04/F-05 Java 侧

---

## 3. Agent Blocks

> Coder 占 [`TASKS.md`](../TASKS.md) **`plan-2026-06-12-qa-agent-followups`** → 追加 **Coder** 块；完工 → **待审**。

----- agent-block begin -----
role: Reviewer
agent: Auto
time: 2026-06-12 19:30
ref: plan-2026-06-12-qa-agent-followups
ref_commit: c44809f,5957f32
verdict: REQUEST_CHANGES
summary: F-03/04/05 大体 OK；F-01 零代码 commit；F-02 路径应用 DB 列非拼装

**已通过 ✅**

| 项 | commit | 结论 |
|---|---|---|
| F-03 v1.1 单测 | `5957f32` | `test_find_project_v11.py` 17 cases，分支表完整 |
| F-04 Java 测试 | `c44809f` | `AgentIntegrationTest` + `@MockBean QaAgentClient` + `@Deprecated`；`08` §7 退役说明 |
| F-05 isHealthy timeout | `5957f32` | connect 用 `timeoutSeconds`；read 固定 5s 可接受 |
| pytest | — | **40 passed** |

**阻塞关单**

1. **F-01 未交付（P0）**：TASKS 写「全部 F-01~F-05」但 **main 无** `config_loader.py` / `config.py` 改造 / `application.yml` host:port / `/health` 的 `config_json`。`08` §5 文档已写，**代码未 commit**（工作区有 WIP，未审）。
2. **F-02 路径解析偏 Java（P1）**：`_resolve_material_version` 用 `proposal_id/material_id` **拼装**路径；Java `ArchiveMaterialPathResolver` 读 **`material_version.storage_path` / `parsed_text_path`** 列。应改为 SELECT 这两列，与 DB 实际存储一致。

**小项（顺手）**

3. F-03 缺 plan 要求的 **turn/ask 层** `agent_sources` 非空断言（`_fmt` 单测已覆盖字段，可补 1 个 contract test）
4. F-05：`QaAgentClient` 仍 **重复** `import java.util.Map`（L18–19）

**修序**：commit F-01 WIP → F-02 改读 storage_path 列 → 补 import 清理 → 再 `待审`。

----- agent-block end -----

---

## 4. 关单检查

- [ ] F-01～F-05 完成 + Reviewer **APPROVED**
- [ ] **`Reviewer` + `verdict: CLOSED`**
- [ ] `git mv` → [`done/`](done/README.md) · TASKS **删行**
