# 数据库 SQL

可执行 SQL 只放本目录。在 MySQL 客户端里 `source` 对应文件即可。

| 文件 | 何时用 |
|---|---|
| [`init.sql`](init.sql) | **空库**全量建表（含 DROP，会删数据） |
| [`migrate_260611_01.sql`](migrate_260611_01.sql) | **已有 `archive_db`** 增量升级（幂等，生产用这个） |

```sql
-- 示例（路径按本机改）
USE archive_db;
source D:/projects-online/deploy/sql/migrate_260611_01.sql;
```

库表字段说明见 [`docs/architecture/DATABASE.md`](../../docs/architecture/DATABASE.md)。
