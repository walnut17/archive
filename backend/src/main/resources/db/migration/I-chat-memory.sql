-- T-I-13: chat_memory 表(多轮对话记忆持久化)
CREATE TABLE IF NOT EXISTS chat_memory (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  message_type VARCHAR(16) NOT NULL,  -- 'user' / 'assistant' / 'system' / 'tool'
  content TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_session_created (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
