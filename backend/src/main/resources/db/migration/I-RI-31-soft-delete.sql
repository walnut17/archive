-- MOD-01 / RI-31: 7 表软删 + version + 归档字段
USE archive_db;

CREATE TABLE IF NOT EXISTS business_term (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    aliases VARCHAR(512),
    category VARCHAR(64),
    definition TEXT,
    standard_definition TEXT,
    source_url VARCHAR(512),
    data_mapping JSON,
    status VARCHAR(32) NOT NULL DEFAULT 'draft' COMMENT 'draft/pending_review/active/deprecated',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bt_name (name),
    INDEX idx_bt_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务术语字典';

ALTER TABLE project
    ADD COLUMN deleted_at DATETIME COMMENT '软删时间' AFTER updated_by,
    ADD COLUMN deleted_by BIGINT COMMENT '软删操作者' AFTER deleted_at,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本' AFTER deleted_by,
    ADD COLUMN archive_status VARCHAR(32) DEFAULT NULL COMMENT '归档状态' AFTER version;

ALTER TABLE proposal
    ADD COLUMN deleted_at DATETIME COMMENT '软删时间' AFTER released_at,
    ADD COLUMN deleted_by BIGINT COMMENT '软删操作者' AFTER deleted_at,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本' AFTER deleted_by;

ALTER TABLE material
    ADD COLUMN deleted_at DATETIME COMMENT '软删时间' AFTER updated_by,
    ADD COLUMN deleted_by BIGINT COMMENT '软删操作者' AFTER deleted_at,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本' AFTER deleted_by,
    ADD COLUMN archived_at DATETIME COMMENT '归档时间' AFTER version;

ALTER TABLE business_term
    ADD COLUMN deleted_at DATETIME COMMENT '软删时间' AFTER updated_at,
    ADD COLUMN deleted_by BIGINT COMMENT '软删操作者' AFTER deleted_at,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本' AFTER deleted_by;

ALTER TABLE audit_log
    ADD COLUMN deleted_at DATETIME COMMENT '软删时间' AFTER created_at,
    ADD COLUMN deleted_by BIGINT COMMENT '软删操作者' AFTER deleted_at,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本' AFTER deleted_by;

ALTER TABLE project_fact_event
    ADD COLUMN deleted_at DATETIME COMMENT '软删时间(逻辑标记, 仍不可物理删)' AFTER resolution_note,
    ADD COLUMN deleted_by BIGINT COMMENT '软删操作者' AFTER deleted_at,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本' AFTER deleted_by;

ALTER TABLE user
    ADD COLUMN deleted_at DATETIME COMMENT '软删时间' AFTER updated_at,
    ADD COLUMN deleted_by BIGINT COMMENT '软删操作者' AFTER deleted_at,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本' AFTER deleted_by;

CREATE INDEX idx_deleted_at ON project(deleted_at);
