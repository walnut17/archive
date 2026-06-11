-- MOD-01 / RI-25: 议案编号系列 + 预留/释放
USE archive_db;

CREATE TABLE IF NOT EXISTS proposal_series (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL COMMENT '系列编码, 如 tx/xc',
    prefix VARCHAR(32) COMMENT '编号前缀',
    current_seq INT NOT NULL DEFAULT 0 COMMENT '当前自增序号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='议案编号系列';

ALTER TABLE proposal
    ADD COLUMN reserved_at DATETIME COMMENT '编号预留时间' AFTER updated_by,
    ADD COLUMN released_at DATETIME COMMENT '编号释放时间' AFTER reserved_at;
