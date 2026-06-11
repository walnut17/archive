-- MOD-01 / RI-44: 旧系统数据导入批次
USE archive_db;

CREATE TABLE IF NOT EXISTS import_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(64) NOT NULL COMMENT '导入类型: project/proposal/...',
    total INT NOT NULL DEFAULT 0,
    success INT NOT NULL DEFAULT 0,
    failed INT NOT NULL DEFAULT 0,
    created_by BIGINT COMMENT '操作者',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type_created (type, created_at),
    CONSTRAINT fk_ib_user FOREIGN KEY (created_by) REFERENCES user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据导入批次';

CREATE TABLE IF NOT EXISTS import_error (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    row_num INT NOT NULL COMMENT 'Excel 行号',
    column_name VARCHAR(128) COMMENT '列名',
    error_msg VARCHAR(1000) NOT NULL,
    INDEX idx_batch (batch_id),
    CONSTRAINT fk_ie_batch FOREIGN KEY (batch_id) REFERENCES import_batch(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入错误明细';
