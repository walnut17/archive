# 需求文档 — `docs/requirements/`

> **业务说什么** — 需求、验收标准、RI 拆解。写/改需求只动这里，不动 architecture 代码。

**文档总索引**：[`docs/README.md`](../README.md) · **协作架构**：[`MULTI-AGENT-REPO-ARCHITECTURE.md`](../../MULTI-AGENT-REPO-ARCHITECTURE.md)

---

## 文件清单

| 文件 | 用途 |
|---|---|
| [`REQUIREMENTS.md`](REQUIREMENTS.md) | 业务需求 v1.1 主文档 |
| [`ARCH-DECOMPOSITION.md`](ARCH-DECOMPOSITION.md) | RI-1~45 拆解底稿 |
| [`AGENT-REQUIREMENTS.md`](AGENT-REQUIREMENTS.md) | Agent 业务访谈 (15 问题 + 验收场景) |
| [`SUPPLEMENTARY-REQUIREMENTS.md`](SUPPLEMENTARY-REQUIREMENTS.md) | 审计补充需求 (P0~P4) |
| [`SIMILAR-PRODUCTS.md`](SIMILAR-PRODUCTS.md) | 行业参考 |

---

## 与任务 / 测试的关系

| 环节 | 位置 |
|---|---|
| 需求 → RI 拆解 | 本目录 `ARCH-DECOMPOSITION.md` |
| 抢 **开发任务** | 根 [`TASKS.md`](../../TASKS.md) + 上表 RI |
| 验收场景（历史） | [`../../test-to-settle/old/ACCEPTANCE-GUIDE.md`](../../test-to-settle/old/ACCEPTANCE-GUIDE.md) · 新案例 [`../../test_task/`](../../test_task/README.md) |
| 自动化案例 | [`../../test_task/`](../../test_task/README.md)（AT-* 对应 RI/MOD） |
| 需求变更评审 | [`../reviews/`](../reviews/README.md)（非 bug） |

---

*架构基线见 [`../architecture/ARCHITECTURE-v2.md`](../architecture/ARCHITECTURE-v2.md)*
