-- ==========================================================
-- Plan G: LLM 用量统计 - llm_call_log 表
-- 创建日期: 2026-06-09
-- 用途: 记录每次 LLM 调用的用户/场景/token/耗时/状态
--
-- **本期范围**:只统计调用次数(智谱 GLM-4-Flash 免费,token 统计暂不启用)
-- token 字段保留 schema(留扩展点),实际数据恒为 NULL
-- 后续若需精确统计,GlmService 增加 parseUsage(responseBody) 即可
-- ==========================================================

CREATE TABLE IF NOT EXISTS llm_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL COMMENT '调用用户 ID(系统调用时为 NULL)',
    username VARCHAR(64) NULL COMMENT '用户名冗余(便于查询)',
    scenario VARCHAR(64) NOT NULL COMMENT '场景:EXTRACTION/TIMEPOINT/COMPARE/QA/RERANK/SUMMARY',
    model VARCHAR(64) NOT NULL COMMENT '模型名,如 glm-4-flash',
    prompt_tokens INT NULL COMMENT 'prompt token 数(智谱不一定返)',
    completion_tokens INT NULL COMMENT 'completion token 数',
    total_tokens INT NULL COMMENT '总 token 数',
    duration_ms INT NOT NULL COMMENT '调用耗时(毫秒)',
    status VARCHAR(16) NOT NULL COMMENT 'SUCCESS/FAILED',
    error_message VARCHAR(500) NULL COMMENT '失败原因(成功时为 NULL)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_scenario_created (scenario, created_at),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT 'LLM 调用日志(用量统计 + 异常排查)';

-- ==========================================================
-- 验证
-- ==========================================================
SELECT 'G-llm-call-log OK' AS migration_status,
       COUNT(*) AS new_table
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'llm_call_log';
