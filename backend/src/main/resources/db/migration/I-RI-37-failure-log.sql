-- MOD-01 / RI-37: 失败兜底日志表
USE archive_db;

CREATE TABLE IF NOT EXISTS failure_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    path VARCHAR(512) NOT NULL COMMENT '失败路径/端点',
    failure_type VARCHAR(64) NOT NULL COMMENT '失败类型枚举',
    error_msg TEXT COMMENT '错误信息',
    stack_trace TEXT COMMENT '堆栈',
    resolved TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已解决',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    resolved_at DATETIME COMMENT '解决时间',
    INDEX idx_path_resolved (path, resolved, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='失败兜底日志';
