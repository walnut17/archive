# 数据库 SQL

可执行 SQL 只放本目录。在 MySQL 客户端里 `source` 对应文件即可。

## 空库 / 全量

| 文件 | 何时用 |
|---|---|
| [`init.sql`](init.sql) | **空库**全量建表（含 DROP，会删数据） |

## 已有 `archive_db` 增量（按顺序执行）

| 顺序 | 文件 | 说明 |
|:---:|---|---|
| 1 | [`migrate_260611_01.sql`](migrate_260611_01.sql) | 基础增量（若已跑过可跳过） |
| 2 | [`migrate_260612_chat_session_context.sql`](migrate_260612_chat_session_context.sql) | qa-agent 多轮项目锁 |
| 3 | [`migrate_260615_chat_session_debt_target.sql`](migrate_260615_chat_session_debt_target.sql) | 多轮债权主题记忆 |
| 4 | [`migrate_260615_analysis_framework.sql`](migrate_260615_analysis_framework.sql) | 后台深度分析框架 |

一键脚本（等价于 2→3→4，**不含** 260611）：

| 文件 | 说明 |
|---|---|
| [`migrate_260615_qa_agent_bundle.sql`](migrate_260615_qa_agent_bundle.sql) | qa-agent 本轮全部 DDL + 种子模板 |

```sql
-- 示例（路径按本机改）
USE archive_db;
source D:/projects_new/projects-online/deploy/sql/migrate_260615_qa_agent_bundle.sql;
```

库表字段说明见 [`docs/architecture/DATABASE.md`](../../docs/architecture/DATABASE.md)。
