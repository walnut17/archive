# 已关闭 Round — `test-to-settle/done/`

> 从 [`test-to-settle/`](../README.md) 根目录 **轮次 CLOSED** 后移入的 `round-*.md`，只读留存。

| Round 文件 | 轮次 | 完工日期 | 摘要 |
|---|---|---|---|
| *(尚无)* | — | — | 首条：`round-2026-06-11-v1.1-deploy.md` 全 bug 闭合后移入 |

---

## 归档条件

- §1 每条 bug：`CLOSED` / `ESCALATED` / `WONTFIX`
- §4 评审完成
- §5 轮次结论已写，状态 **`CLOSED`**

## 归档命令

```bash
git mv test-to-settle/round-YYYY-MM-DD-*.md test-to-settle/done/
```

同步：从 [`../STATUS.md`](../STATUS.md) 删活跃行 · 本表追加 · [`TASKS.md`](../../TASKS.md) 清对应 DEBUG 行。

详见 [`CODE-REVIEWER.md`](../../CODE-REVIEWER.md) §4.2。
