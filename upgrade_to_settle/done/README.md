# 已归档 Case — `upgrade_to_settle/done/`

> 审查员写 **Closer** 块后，从 [`upgrade_to_settle/`](../README.md) 根目录移入的 plan，只读留存。

| Plan 文件 | 路由 ID | 完工日期 | 摘要 |
|---|---|---|---|
| [`plan-2026-06-11-deploy-pipeline.md`](plan-2026-06-11-deploy-pipeline.md) | plan-2026-06-11-deploy-pipeline | 2026-06-12 | 生产部署 SOP + migrate 治理 + application-prod |
| [`plan-2026-06-11-archive-local-fs-tools.md`](plan-2026-06-11-archive-local-fs-tools.md) | plan-2026-06-11-archive-local-fs-tools | 2026-06-12 | archive_fs PathGuard + list/grep/read（4 评审意见全部修） |
| [`plan-2026-06-11-agent-intent-classification.md`](plan-2026-06-11-agent-intent-classification.md) | plan-2026-06-11-agent-intent-classification | 2026-06-12 | Agent 域约束 + 拒答规则（1 P1 已修） |
| [`plan-2026-06-11-chat-ui.md`](plan-2026-06-11-chat-ui.md) | plan-2026-06-11-chat-ui | 2026-06-12 | 聊天式 Knowledge UI（1 P2 + 自我评审 2 P2 全部修） |
| [`plan-2026-06-11-test-governance.md`](plan-2026-06-11-test-governance.md) | plan-2026-06-11-test-governance | 2026-06-12 | 第 45 测例 + test-strategy（1 P0 guard 已加） |

---

## 归档命令

```bash
git mv upgrade_to_settle/plan-YYYY-MM-DD-*.md upgrade_to_settle/done/
```

同步：**[`TASKS.md`](../../TASKS.md) 删除本 case 行** · 可选更新 [`../STATUS.md`](../STATUS.md) · 本表索引。

**关单职责**：由**代码审查员**按 [`CODE-REVIEWER.md`](../../CODE-REVIEWER.md) 写 **Closer** 后执行（勿由 Coder 代关）。

详见 [`CASE-FORMAT.md`](../../CASE-FORMAT.md)。
