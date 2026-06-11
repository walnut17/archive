# test_bug — <简述>

> **状态**：`OPEN`（待收入 `round-*.md` §1）  
> **规则**：本文件是 **bug 入口**；修复闭环在 [`round-*.md`](round-TEMPLATE.md) §1～§4，由 Recorder / Analyst / Fix / Reviewer 接手。  
> **来源**：`AUTO`（来自 [`test_task/`](../test_task/README.md) 自动化案例）

---

## 0. 元信息

| 字段 | 内容 |
|---|---|
| **发现时间** | YYYY-MM-DD HH:mm |
| **发现 Agent** | *填写执行测试的 Agent* |
| **关联 AT 任务** | `AT-XXX`（[`TASKS.md`](../TASKS.md)） |
| **关联案例** | [`test_task/AT-XXX-….md`](../test_task/case-TEMPLATE.md) |
| **环境** | 本地 / `182.168.1.125` / CI |
| **代码基线** | commit hash |
| **Round Bug ID** | *Recorder 收入 round 后回填 `T-MMDD-NN`* |

---

## 1. 现象

| 严重度 | 模块 | 现象 |
|---|---|---|
| P? | | |

---

## 2. 复现步骤

1. （与 test_task 案例步骤一致，可引用案例文件）
2. …

**实际输出 / 日志**：

```text
粘贴关键错误
```

---

## 3. 预期 vs 实际

| | 内容 |
|---|---|
| **预期** | （来自 test_task 案例 §1.2） |
| **实际** | |

---

## 4. 后续（其他 Agent）

| 步骤 | Agent | 动作 |
|---|---|---|
| 1 | **Recorder** | 将本条收入当前 [`round-*.md`](round-2026-06-11-v1.1-deploy.md) **§1**，分配 `T-MMDD-NN`，来源 `AUTO`；回填本节 **Round Bug ID** |
| 2 | **Analyst** | round **§2** 根因与建议 |
| 3 | **Fix** | round **§3** 小修 + commit |
| 4 | **Reviewer** | round **§4** 评审 → bug `CLOSED` |

**发现方请勿**：在 test_bug 之外偷偷改业务代码（除非用户明确 hotfix）；大改 → [`complexity.md`](complexity.md)。

---

## 5. 收入 round 记录（Recorder 填）

| 字段 | 内容 |
|---|---|
| **round 文件** | |
| **Bug ID** | `T-MMDD-NN` |
| **Recorder** | |
| **收入时间** | |

---

*模板 · 见 [test-to-settle/README.md](./README.md) · [test_task/README.md](../test_task/README.md)*
