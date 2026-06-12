-- v1.2: 多轮项目记忆穿透 — chat_session_context 表
-- 2026-06-12 (PM 兼 Coder 干的紧急回退)

CREATE TABLE IF NOT EXISTS chat_session_context (
    session_id      VARCHAR(64)  NOT NULL COMMENT '会话 ID (UUID)',
    project_code    VARCHAR(64)  NULL     COMMENT '锁定的项目编号 (PRJ-YYYY-NNN)',
    project_name    VARCHAR(256) NULL     COMMENT '锁定的项目名 (冗余, 提速显示)',
    last_tool       VARCHAR(64)  NULL     COMMENT '最后命中的工具 (find_project / search_fulltext / ...)',
    last_confidence VARCHAR(16)  NULL     COMMENT '最后置信度 (SAME_CONFIRMED / SAME_PROBABLY / ...)',
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id),
    INDEX idx_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='v1.2 多轮项目锁 (用于流式 + 多轮指代词解析)';
