# 文档目录 — `docs/`

> **本目录放长期文档**；`docs/` 根下**只有本 README** 一个文件，其余全部在子目录里。
>
> 代码在 `backend/` / `frontend/` · **任务路由** [`../TASKS.md`](../TASKS.md)（DEBUG / UPGRADE / AT-*）· DEBUG 详情 [`../test-to-settle/`](../test-to-settle/) · UPGRADE 详情 [`../upgrade_to_settle/`](../upgrade_to_settle/) · 自动化案例 [`../test_task/`](../test_task/)

---

## 1. 设计逻辑（先看这个）

文档按 **「写什么 → 怎么设计 → 怎么评审 → 怎么运维 → 怎么交接」** 分层，不要混在根目录：

```text
  业务说什么          技术怎么建           过去踩过什么坑        怎么部署跑起来         版本怎么交
       │                  │                     │                    │                  │
       ▼                  ▼                     ▼                    ▼                  ▼
 requirements/      architecture/           reviews/           operations/          handoff/
   需求 & RI            架构 & 模块            review & 踩坑         部署 & 规范          交付指南
```

**和仓库其它目录的分工：**

| 位置 | 放什么 | 不放什么 |
|---|---|---|
| **`docs/`** | 需求、架构、review、运维、交接等**长期文档** | 代码、测试代码、SQL 脚本 |
| **[`test-to-settle/`](../test-to-settle/)** | **DEBUG 详情**：`round-*.md`、`test_bug-*.md`、`complexity.md` | 通过记录、案例定义 |
| **[`upgrade_to_settle/`](../upgrade_to_settle/)** | **UPGRADE 详情** plan（活跃 + `done/` 归档） | — |
| **[`test_task/`](../test_task/)** | 自动化**案例** + **PASS** 时 §3 执行历史 | bug 修复、round 闭环 |
| **[`TASKS.md`](../TASKS.md)**（根） | **路由 + 占用**（DEBUG / UPGRADE / AT-*）；不含任务全文 | 详情全文 |
| **`backend/src/test-to-settle/`** | Java **单元/集成测试代码** | Markdown 文档 |
| **`deploy/`** | Caddy / WinSW / **SQL 迁移脚本** |  prose 部署说明（在 `operations/`） |

**记「操作」vs 记「问题」：**

| 我要记… | 写哪里 |
|---|---|
| 在 125 上执行了哪些命令、步骤 | [`operations/deployment_log.md`](operations/deployment_log.md) |
| 发现 bug、四轮次（记/析/改/审） | [`../test-to-settle/round-*.md`](../test-to-settle/round-2026-06-11-v1.1-deploy.md) · 或 `test_bug-*.md` 入口 |
| 自动化案例 **通过** | [`../test_task/`](../test_task/README.md) 案例 §3 执行历史 |
| 自动化案例 **失败** | [`../test-to-settle/test_bug-TEMPLATE.md`](../test-to-settle/test_bug-TEMPLATE.md) → round §1 |
| 大改暂挂（不进 TASKS） | [`../test-to-settle/complexity.md`](../test-to-settle/complexity.md)（出站删行） |
| **升级 plan（UPGRADE 详情）** | [`../upgrade_to_settle/plan-*.md`](../upgrade_to_settle/plan-2026-06-11-archive-local-fs-tools.md) → TASKS UPGRADE 行 |
| 代码/架构 **评审对线**（OPEN→CLOSED） | [`reviews/review-*.md`](reviews/README.md) · 模板 [`review-TEMPLATE.md`](reviews/review-TEMPLATE.md) |

---

## 2. 快速查找（30 秒定位）

| 我想… | 去这里 |
|---|---|
| 了解业务要做什么 | [`requirements/REQUIREMENTS.md`](requirements/REQUIREMENTS.md) |
| 抢 coder 任务（DEBUG / UPGRADE） | 根 [`TASKS.md`](../TASKS.md) **🎯 任务路由** → 详情路径 |
| 历史 RI / MOD | TASKS **📜 历史占表** + [`requirements/ARCH-DECOMPOSITION.md`](requirements/ARCH-DECOMPOSITION.md) |
| 抢 AT 自动化测试 | [`TASKS.md`](../TASKS.md) AT 节 + [`../test_task/`](../test_task/README.md) |
| 看表结构 / 加字段 | [`architecture/DB-SCHEMA-v2.md`](architecture/DB-SCHEMA-v2.md) |
| 看系统架构 / 模块划分 | [`architecture/ARCHITECTURE-v2.md`](architecture/ARCHITECTURE-v2.md) |
| Agent / Spring AI 怎么定的 | [`architecture/AGENT-FRAMEWORK-DECISION.md`](architecture/AGENT-FRAMEWORK-DECISION.md) |
| 配 GLM API key | [`operations/GLM-KEY-SETUP.md`](operations/GLM-KEY-SETUP.md) |
| 部署到 125 / Caddy / WinSW | [`operations/DEPLOYMENT.md`](operations/DEPLOYMENT.md) + [`handoff/v1.1-DEPLOY-GUIDE.md`](handoff/v1.1-DEPLOY-GUIDE.md) |
| 日常启停 / 看日志 | [`operations/RUNBOOK.md`](operations/RUNBOOK.md) |
| 避免重复踩坑 | [`reviews/LESSONS-LEARNED.md`](reviews/LESSONS-LEARNED.md) |
| 开/跟 review 对线 | [`reviews/README.md`](reviews/README.md) · `cp review-TEMPLATE.md` |
| 看 OPEN 的 review / 踩坑 | [`reviews/`](reviews/README.md) → 扫 OPEN + `LESSONS-LEARNED.md` |
| 自主跑用例 / deploy 发现 bug | [`../test-to-settle/README.md`](../test-to-settle/README.md) → `test_bug` / `round` |
| 大改中转 / PM 拍板 | [`../test-to-settle/complexity.md`](../test-to-settle/complexity.md)（升格后进 upgrade_to_settle + TASKS） |
| 端到端验收（历史参考） | [`../test-to-settle/old/ACCEPTANCE-GUIDE.md`](../test-to-settle/old/ACCEPTANCE-GUIDE.md) · 新案例 [`../test_task/`](../test_task/README.md) |
| 提交规范 / commit 格式 | [`operations/DEV-STANDARDS.md`](operations/DEV-STANDARDS.md) |
| **多 Agent 协作架构（可套用）** | [`../MULTI-AGENT-REPO-ARCHITECTURE.md`](../MULTI-AGENT-REPO-ARCHITECTURE.md) |
| v1.1 交付物清单 | [`handoff/v1.1-DELIVERY-REPORT.md`](handoff/v1.1-DELIVERY-REPORT.md) |

---

## 3. 子目录说明

### [`requirements/`](requirements/README.md) — 需求（What）

**业务目标、验收标准、RI 拆解。** 写/改需求只动这里，不动 architecture。

| 首读 | 文件 |
|---|---|
| 全员 | `REQUIREMENTS.md` — 业务需求 v1.1 主文档 |
| 架构师 / 程序员 | `ARCH-DECOMPOSITION.md` — RI-1~45 |
| Agent 业务场景 | `AGENT-REQUIREMENTS.md` — 访谈原话 + 15 问题 |
| 历史补充 | `SUPPLEMENTARY-REQUIREMENTS.md` · `SIMILAR-PRODUCTS.md` |

### [`architecture/`](architecture/README.md) — 架构与模块设计（How）

**技术方案、DB schema、Agent 设计。** 与 requirements 一一对应：需求 §X.Y → RI-N → 架构/表/接口。

| 首读 | 文件 |
|---|---|
| 架构基线 | `ARCHITECTURE-v2.md` |
| 数据库 | `DB-SCHEMA-v2.md` |
| Agent 实施 | `AGENT-IMPL-PLAN.md` + `AGENT-FRAMEWORK-DECISION.md` |
| 分章细读 | `01-arch-overview.md` ~ `06-requirements-gap-analysis.md` |
| 历史决策 | `history/architecture-v3-final.md` 等 |

### [`reviews/`](reviews/README.md) — 评审对线与踩坑

**各 Agent 评审、回复、跟进的正式场所。** Review Agent **新开** `review-*.md` 写意见；Subject Agent **在下方**写回复；**仅 Review Agent 可宣布 `CLOSED`**。与 [`test-to-settle/round-*.md`](../test-to-settle/README.md) §4 Reviewer（验收 bug）分工不同。

| 类型 | 文件 |
|---|---|
| **工作流 + 模板** | [`README.md`](reviews/README.md) · [`review-TEMPLATE.md`](reviews/review-TEMPLATE.md) |
| 踩坑大全 | [`LESSONS-LEARNED.md`](reviews/LESSONS-LEARNED.md) |
| 历史静态 review | `2026-06-09-plan-i-p0-review.md`、`sisyphus-review-*.md` 等 |
| 部署 handoff | `2026-06-10-prod-deploy-handoff.md` |

### [`operations/`](operations/README.md) — 运维与规范（How to run）

**部署、环境、密钥、日志、开发规范。** 上生产 / 排错 / 提交代码前查这里。

| 类型 | 文件 |
|---|---|
| 部署步骤 | `DEPLOYMENT.md` |
| 运维手册 | `RUNBOOK.md` |
| 环境依赖 | `ENVIRONMENT-DEPENDENCIES.md` |
| GLM 配置 | `GLM-KEY-SETUP.md` |
| 部署时间线 (v1.1+) | `deployment_log.md` |
| 开发规范 | `DEV-STANDARDS.md` |

### [`handoff/`](handoff/README.md) — 版本交接

**特定版本的交付报告与部署指南**，给接手人或生产验收用。

| 文件 | 用途 |
|---|---|
| [`README.md`](handoff/README.md) | 本目录索引 |
| `v1.1-DELIVERY-REPORT.md` | v1.1 交付物与验收状态 |
| `v1.1-DEPLOY-GUIDE.md` | v1.1 生产部署步骤（125） |

### [`MULTI-AGENT-REPO-ARCHITECTURE.md`](../MULTI-AGENT-REPO-ARCHITECTURE.md) — 协作架构（根目录）

**多 Agent 文件协作的总说明**：`TASKS` / `test_task` / `test` / `docs/reviews` 分层、角色、模板、新项目套用清单。**给其他项目复制时先读此文件。**

---

## 4. 推荐阅读顺序（按角色）

| 角色 | 顺序 |
|---|---|
| **新接手程序员** | 根 `README` §1 → [`MULTI-AGENT-REPO-ARCHITECTURE.md`](../MULTI-AGENT-REPO-ARCHITECTURE.md) → `reviews/LESSONS-LEARNED.md` → `TASKS.md` 抢任务 |
| **架构师** | `requirements/REQUIREMENTS.md` → `architecture/ARCHITECTURE-v2.md` + `DB-SCHEMA-v2.md` → 写 RI |
| **部署 / 联调** | `handoff/v1.1-DEPLOY-GUIDE.md` → `deployment_log.md` → bug 进 `test-to-settle/` |
| **测试 Agent** | [`test_task/README.md`](../test_task/README.md) 抢 AT-*；FAIL → [`test-to-settle/README.md`](../test-to-settle/README.md) |
| **Review Agent** | [`reviews/README.md`](reviews/README.md) · `cp review-TEMPLATE.md` |

---

## 5. 路径迁移备忘 (2026-06-11)

旧链接若 404，查下表：

| 旧路径 | 新路径 |
|---|---|
| 根 `architecture-v*.md` | `architecture/history/` |
| 根 `DEPLOYMENT.md` / `RUNBOOK.md` | `operations/` |
| 根 `SUPPLEMENTARY-REQUIREMENTS.md` | `requirements/` |
| 根 `VERIFICATION-REPORT.md` | `../test-to-settle/old/` |
| `docs/ARCHITECTURE-v2.md` 等根级 md | `architecture/` |
| `docs/LESSONS-LEARNED.md` | `reviews/` |
| `docs/DEV-STANDARDS.md` | `operations/` |
| `docs/M1-*.md` / 旧验收指南 | `../test-to-settle/old/` |

---

*维护：增删文档时同步更新本子目录 README + 根 [`README.md`](../README.md) + [`MULTI-AGENT-REPO-ARCHITECTURE.md`](../MULTI-AGENT-REPO-ARCHITECTURE.md)。*
