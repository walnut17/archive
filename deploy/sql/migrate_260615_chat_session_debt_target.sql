-- 2026-06-15: qa-agent 本轮增量合集（在 archive_db 上执行）
-- 包含: chat_session 债权列 + 后台分析框架
-- 若 chat_session_context 表不存在，请先跑 migrate_260612_chat_session_context.sql

-- ========== 1) 多轮债权主题 ==========
ALTER TABLE chat_session_context
    ADD COLUMN last_debt_target VARCHAR(256) NULL
        COMMENT '上一轮确认的债权标的 (如 南安市岭兜建材二厂债权)'
        AFTER project_name;

-- ========== 2) 后台深度分析框架 ==========
SOURCE migrate_260615_analysis_framework.sql;
