# 已归档 Case — `upgrade_to_settle/done/`

> 审查员 **`Reviewer(CLOSED)`** 后，从 [`upgrade_to_settle/`](../README.md) 根目录移入的 plan，只读留存。

| Plan 文件 | 路由 ID | 完工日期 | 摘要 |
|---|---|---|---|
| [`plan-2026-06-11-deploy-pipeline.md`](plan-2026-06-11-deploy-pipeline.md) | plan-2026-06-11-deploy-pipeline | 2026-06-12 | 生产部署 SOP + migrate 治理 + application-prod |
| [`plan-2026-06-12-qa-agent-streaming-multiturn.md`](plan-2026-06-12-qa-agent-streaming-multiturn.md) | plan-2026-06-12-qa-agent-streaming-multiturn | 2026-06-12 | v1.2 升级: P0 SSE 流式 + 多轮项目锁 + TUI 工具（PM 干）|
| [`plan-2026-06-12-qa-agent-tool-recovery.md`](plan-2026-06-12-qa-agent-tool-recovery.md) | plan-2026-06-12-qa-agent-tool-recovery | 2026-06-12 | v1.2 升级: P1 3 级降级 + 死循环自愈 + find_project 4 级链 + prompts v1.2（PM 干）|
| [`plan-2026-06-11-archive-local-fs-tools.md`](plan-2026-06-11-archive-local-fs-tools.md) | plan-2026-06-11-archive-local-fs-tools | 2026-06-12 | archive_fs PathGuard + list/grep/read（4 评审意见全部修） |
| [`plan-2026-06-11-agent-intent-classification.md`](plan-2026-06-11-agent-intent-classification.md) | plan-2026-06-11-agent-intent-classification | 2026-06-12 | Agent 域约束 + 拒答规则（1 P1 已修） |
| [`plan-2026-06-11-chat-ui.md`](plan-2026-06-11-chat-ui.md) | plan-2026-06-11-chat-ui | 2026-06-12 | 聊天式 Knowledge UI（1 P2 + 自我评审 2 P2 全部修） |
| [`plan-2026-06-11-test-governance.md`](plan-2026-06-11-test-governance.md) | plan-2026-06-11-test-governance | 2026-06-12 | 第 45 测例 + test-strategy（1 P0 guard 已加） |
| [`plan-2026-06-12-agent-source-display.md`](plan-2026-06-12-agent-source-display.md) | plan-2026-06-12-agent-source-display | 2026-06-12 | Agent 模式来源区 Source DTO + extractSources + 前端分组 |
| [`plan-2026-06-12-frontend-error-standard.md`](plan-2026-06-12-frontend-error-standard.md) | plan-2026-06-12-frontend-error-standard | 2026-06-12 | errorHandler toast + /api/client-error 上报 + DEV-STANDARDS §12 |

---

## 归档命令

```bash
git mv upgrade_to_settle/plan-YYYY-MM-DD-*.md upgrade_to_settle/done/
```

同步：**[`TASKS.md`](../../TASKS.md) 删除本 case 行** · 可选更新 [`../STATUS.md`](../STATUS.md) · 本表索引。

**关单职责**：**代码审查员**写 **Reviewer(CLOSED)** 后 `git mv`（见 [`CODE-REVIEWER.md`](../../CODE-REVIEWER.md)）。

详见 [`CASE-FORMAT.md`](../../CASE-FORMAT.md)。
