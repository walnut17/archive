-- ==========================================================
-- I-13: 多轮对话记忆 chat_memory 表 (Spring AI 1.1)
--
-- Spring AI 1.1 公开 API: JdbcChatMemoryRepository 默认表名是 spring_ai_chat_memory
-- (注: Spring AI 1.1 不提供 tableName() 配置项, 硬编码在源码 SQL 模板里)
--
-- 字段含义参考 Spring AI 1.1 JdbcChatMemoryRepositoryDialect 的 SQL 模板:
--   conversation_id: 会话 ID (Sisyphus / Mavis 修)
--   content: 消息内容 (TEXT)
--   type: 消息类型 (USER/ASSISTANT/SYSTEM/TOOL)
--   timestamp: 时间戳
-- ==========================================================

CREATE TABLE IF NOT EXISTS spring_ai_chat_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话 ID',
    content TEXT NOT NULL COMMENT '消息内容',
    type VARCHAR(16) NOT NULL COMMENT 'user / assistant / system / tool',
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation_timestamp (conversation_id, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 多轮对话记忆 (Spring AI 1.1 MessageChatMemoryAdvisor)';

-- 验证
SELECT 'I-chat-memory OK' AS migration_status,
       COUNT(*) AS table_exists
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'spring_ai_chat_memory';
