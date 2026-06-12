# upgrade_to_settle — 活跃 Plan 索引

> **Coder** → [`TASKS.md`](../TASKS.md) **🎯 UPGRADE 路由**  
> **代码审查员** → [`CODE-REVIEWER.md`](../CODE-REVIEWER.md) · 下方 **「待代码审查」**  
> 完工 plan → [`done/`](done/README.md)

---

## 待代码审查（审查员入口）

| Plan 文件 | Plan ID | Plan 状态 | 审查状态 | 待审项 | 审查员 | 更新 |
|---|---|---|---|---|---|---|
| [`plan-2026-06-11-archive-local-fs-tools.md`](plan-2026-06-11-archive-local-fs-tools.md) | **UP-0611-01** | `IN_PROGRESS` | `待审` | 3 P0 + 1 P1 | 投委会档案项目PM | 2026-06-11 |
| [`plan-2026-06-11-agent-intent-classification.md`](plan-2026-06-11-agent-intent-classification.md) | **UP-0611-02** | `IN_PROGRESS` | `待审` | 1 P1（工具数写错） | 投委会档案项目PM | 2026-06-11 |
| [`plan-2026-06-11-deploy-pipeline.md`](plan-2026-06-11-deploy-pipeline.md) | **UP-0611-03** | `IN_PROGRESS` | `待审` | 0 | 投委会档案项目PM | 2026-06-11 |
| [`plan-2026-06-11-chat-ui.md`](plan-2026-06-11-chat-ui.md) | **UP-0611-04** | `IN_PROGRESS` | `待审` | 1 P2（死代码） | 投委会档案项目PM | 2026-06-11 |
| [`plan-2026-06-11-test-governance.md`](plan-2026-06-11-test-governance.md) | **UP-0611-05** | `IN_PROGRESS` | `待审` | 1 P0（test45 不能跑） | 投委会档案项目PM | 2026-06-11 |

**审查状态**：`—`（未提交）· `待审` · `审阅中` · `有问题` · `已通过` · `CLOSED`（已移 `done/`）

> **2026-06-11 评审结果摘要**：
> - **UP-0611-01**: 3 P0（缺 `ArchiveMaterialPathResolver` + 2 单测） + 1 P1（"7 个工具"应为"8 个"） → **`REQUEST_CHANGES`**
> - **UP-0611-02**: 1 P1（"6 个工具"应为"8 个"） → **`REQUEST_CHANGES`**
> - **UP-0611-03**: 0 问题 → **`APPROVED`**
> - **UP-0611-04**: 1 P2（`Knowledge.vue` 死代码 `exampleQuestions`） → **`APPROVED`**（P2 不阻塞）
> - **UP-0611-05**: 1 P0（`test45` 未 mock ChatClient，CI 必崩） → **`REQUEST_CHANGES`**

详细评审意见见各 plan §6。

---

## 活跃 plan（全量）

| Plan 文件 | Plan ID | 状态 | 摘要 | 更新 |
|---|---|---|---|---|
| [`plan-2026-06-11-archive-local-fs-tools.md`](plan-2026-06-11-archive-local-fs-tools.md) | **UP-0611-01** | `IN_PROGRESS` | archive_fs ls/grep/read | 2026-06-11 |
| [`plan-2026-06-11-agent-intent-classification.md`](plan-2026-06-11-agent-intent-classification.md) | **UP-0611-02** | `IN_PROGRESS` | 离题拒答 + 意图分类（C-0611-01） | 2026-06-11 |
| [`plan-2026-06-11-deploy-pipeline.md`](plan-2026-06-11-deploy-pipeline.md) | **UP-0611-03** | `IN_PROGRESS` | 部署 SOP / migrate / ddl-auto（C-0611-02～05/09） | 2026-06-11 |
| [`plan-2026-06-11-chat-ui.md`](plan-2026-06-11-chat-ui.md) | **UP-0611-04** | `IN_PROGRESS` | 聊天式 UI（C-0611-08） | 2026-06-11 |
| [`plan-2026-06-11-test-governance.md`](plan-2026-06-11-test-governance.md) | **UP-0611-05** | `IN_PROGRESS` | 测试治理（C-0611-10/11） | 2026-06-11 |

> plan **CLOSED** 后 `git mv` → `done/`，从本表删除，写入 [`done/README.md`](done/README.md)。
