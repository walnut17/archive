# 架构与模块设计 — `docs/architecture/`

> **技术怎么建** — 架构、DB schema、Agent 设计。与 requirements 对应：需求 §X.Y → RI-N → 本目录。

**文档总索引**：[`docs/README.md`](../README.md) · **协作架构**：[`MULTI-AGENT-REPO-ARCHITECTURE.md`](../../MULTI-AGENT-REPO-ARCHITECTURE.md)

---

## 文件清单

| 文件 | 用途 |
|---|---|
| **[`DATABASE.md`](DATABASE.md)** | **Agent 首读** — 全库 28 表、字段、关系、FULLTEXT、JOIN 示例（对齐 `init.sql`） |
| [`ARCHITECTURE-v2.md`](ARCHITECTURE-v2.md) | 现行架构基线 (增补版) |
| [`DB-SCHEMA-v2.md`](DB-SCHEMA-v2.md) | v2 迁移脚本、种子数据（字段细节以 DATABASE.md / init.sql 为准） |
| [`AGENT-IMPL-PLAN.md`](AGENT-IMPL-PLAN.md) | Plan I Agent 实施总方案 |
| [`AGENT-FRAMEWORK-DECISION.md`](AGENT-FRAMEWORK-DECISION.md) | Spring AI 框架决策 |
| [`AGENT-RESEARCH.md`](AGENT-RESEARCH.md) | Agent 方案调研 |
| [`ARCH-REUSE-AUDIT.md`](ARCH-REUSE-AUDIT.md) | 可复用模块审计 |
| [`01-arch-overview.md`](01-arch-overview.md) ~ [`06-requirements-gap-analysis.md`](06-requirements-gap-analysis.md) | 分章架构 |
| [`07-archive-fs-agent-tools.md`](07-archive-fs-agent-tools.md) | Agent 只读 archive 磁盘工具 (plan-2026-06-11-archive-local-fs-tools) |
| [`08-qa-agent-python-service.md`](08-qa-agent-python-service.md) | Python FastAPI qa-agent 微服务 (plan-2026-06-12-qa-python-upload-first) |
| [`history/`](history/) | 历史架构 v1 / v2-lite / v3-final |

---

## 与协作流程

| 环节 | 位置 |
|---|---|
| 业务需求 | [`../requirements/`](../requirements/README.md) |
| MOD/RI **开发占用** | 根 [`TASKS.md`](../../TASKS.md) |
| 架构/MOD **代码评审** | [`../reviews/`](../reviews/README.md) |
| 实现 **缺陷** | [`../../test-to-settle/`](../../test-to-settle/README.md) |
| **功能升级 plan** | [`../../upgrade_to_settle/`](../../upgrade_to_settle/README.md)（含 [`done/`](../../upgrade_to_settle/done/README.md)） |
| 集成测试案例 | [`../../test_task/`](../../test_task/README.md) |

---

*评审与踩坑见 [`../reviews/LESSONS-LEARNED.md`](../reviews/LESSONS-LEARNED.md)*
