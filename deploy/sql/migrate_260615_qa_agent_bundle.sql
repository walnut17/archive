-- 2026-06-15: qa-agent 本轮增量执行清单（MySQL 客户端内按序 source）
--
-- USE archive_db;
-- source <本目录>/migrate_260612_chat_session_context.sql;
-- source <本目录>/migrate_260615_chat_session_debt_target.sql;
-- source <本目录>/migrate_260615_analysis_framework.sql;
--
-- 说明:
-- - migrate_260615_chat_session_debt_target.sql 若列已存在会报错，可忽略
-- - migrate_260611_01.sql 为更早增量，新环境若已跑过 init.sql 可跳过

SELECT '请按本文件头部注释顺序 source 三个 migrate 文件' AS hint;
