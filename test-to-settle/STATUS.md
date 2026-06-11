# test-to-settle/ 状态索引

> **Coder** → [`TASKS.md`](../TASKS.md) **🎯 任务路由**  
> **代码审查员** → [`CODE-REVIEWER.md`](../CODE-REVIEWER.md) · 下方 **「待代码审查」**  
> **大改/拍板** → [`complexity.md`](complexity.md)

---

## 待代码审查（审查员入口）

| 文件 | 轮次状态 | 审查状态 | 待审项 | 审查员 | 最后更新 |
|---|---|---|---|---|---|
| [`round-2026-06-11-v1.1-deploy.md`](round-2026-06-11-v1.1-deploy.md) | `IN_PROGRESS` | **待审** | T-0611-12/13/14/16/17（§3 FIXED，§4 VERIFY） | — | 2026-06-11 |

**审查状态**：`待审` · `审阅中` · `有问题` · `已通过` · `CLOSED`（已移 `done/`）

---

## round/（活跃）

| 文件 | 状态 | 待办摘要 | 最后更新 |
|---|---|---|---|
| `round-2026-06-11-v1.1-deploy.md` | `IN_PROGRESS` | TASKS：T-08/09/18/20；complexity：C-01～11 | 2026-06-11 |

> 轮次 **CLOSED** 后移 [`done/`](done/README.md)，并从本表删除。

## test_bug/

| 文件 | 状态 | 关联 round | 严重度 | 处理方 |
|---|---|---|---|---|
| _(暂无)_ | — | — | — | — |

## complexity/

- 活跃路由 §2 行数（出站即删）：**11** 条 `PENDING`（C-0611-01～11）
- 详见 [`complexity.md`](complexity.md)

## TASKS 路由（coder，非审查员）

| ID | 状态 |
|---|---|
| T-0611-08 | VERIFY |
| T-0611-09 | 未开发 |
| T-0611-18 | 未开发 |
| T-0611-20 | VERIFY |

---

## 维护规则

| 谁 | 何时 | 做什么 |
|---|---|---|
| **代码审查员** | 开审 / 通过 / 归档 | 更新 **待代码审查** + §4；CLOSED → `done/` |
| Fix Agent | §3 提交 | 待审项出现时 STATUS 审查状态 → `待审` |
| Fix / Reviewer | bug 状态变 | 同步 TASKS（若适用） |
