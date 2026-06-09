-- ==========================================================
-- I-13: 多轮对话记忆 chat_memory 表
-- 用途: MessageChatMemoryAdvisor 持久化多轮对话上下文
-- 配合: JdbcChatMemoryRepository (Spring AI 1.1) / tableName=chat_memory
-- ==========================================================

CREATE TABLE IF NOT EXISTS chat_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话 ID (Spring AI 1.1 字段名)',
    message_type VARCHAR(16) NOT NULL COMMENT 'user / assistant / system / tool',
    content TEXT NOT NULL COMMENT '消息内容',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation_created (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent 多轮对话记忆(Spring AI 1.1 MessageChatMemoryAdvisor)';

-- 验证
SELECT 'I-chat-memory OK' AS migration_status,
       COUNT(*) AS table_exists
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'chat_memory';
