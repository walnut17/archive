# 已关闭 Case — `test-to-settle/done/`

> 审查员写 **Closer** 块后，从 [`test-to-settle/`](../README.md) 根目录移入的 `round-*.md`，只读留存。

| Case 文件 | Case ID | 完工日期 | 摘要 |
|---|---|---|---|
| *(尚无)* | — | — | 首条：`round-2026-06-11-v1.1-deploy` 关单后移入 |

---

## 归档条件

- 各 `T-*` 已 APPROVED / ESCALATED / WONTFIX
- 已有 **`role: Closer`** 块
- 元信息 `Case 状态` = **`CLOSED`**

## 归档命令

```bash
git mv test-to-settle/round-YYYY-MM-DD-*.md test-to-settle/done/
```

同步：**[`TASKS.md`](../../TASKS.md) 删除本 case 行** · 可选更新 [`../STATUS.md`](../STATUS.md) · 本表追加。

详见 [`CODE-REVIEWER.md`](../../CODE-REVIEWER.md) · [`CASE-FORMAT.md`](../../CASE-FORMAT.md)。
