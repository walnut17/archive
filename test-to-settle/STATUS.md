# test-to-settle/ 状态索引

> **接手 agent 上手**：扫一眼本表，挑 `OPEN` 行处理；`CLOSED` 行做参考。
> **bug 状态变更者**（Fix / Reviewer）改 round / test_bug 时**必须同步**本表。
> **本表不是 git push 真相**——只供肉眼快速定位。

---

## round/

| 文件 | 状态 | OPEN bug 数 | 最后更新 | 责任 agent |
|---|---|---|---|---|
| `round-2026-06-11-v1.1-deploy.md` | `OPEN` | 4 PENDING + 3 VERIFY | 2026-06-11 | 阿根廷 / Sisyphus |
| _(历史 round 文件归档到 `old/`)_ | `CLOSED` | 0 | — | — |

## test_bug/

| 文件 | 状态 | 关联 round | 严重度 | 处理方 |
|---|---|---|---|---|
| _(暂无独立 test_bug 案例，bug 全在 round-0611 §1.3)_ | — | — | — | — |

## complexity/

- **当前 PENDING**：7 条（C-0611-01 ~ 07）
- 详见 [`complexity.md`](complexity.md) §2 表格

---

## 上手指南

1. 打开本表 → 看 `OPEN` 行
2. 读对应 round / test_bug 文件
3. §2 → §3 → §4 走流程
4. 状态变 → 同步本表

## 维护规则

| 谁 | 何时 | 做什么 |
|---|---|---|
| **bug 状态变更者** | 改 round / test_bug 状态 | 同步本表 |
| **新 round / test_bug Owner** | 写新文件时 | 追加一行 `OPEN` |
| **接手 agent** | 进 test-to-settle/ 时 | 扫本表挑活 |
| **严禁** | 改了 round 不更新本表 | — |
