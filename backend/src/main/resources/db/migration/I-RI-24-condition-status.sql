-- MOD-01 / RI-24: 议案附条件通过跟踪
USE archive_db;

ALTER TABLE proposal
    ADD COLUMN condition_text TEXT COMMENT '附条件通过的条件描述' AFTER decision,
    ADD COLUMN condition_status VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/MET/UNMET' AFTER condition_text,
    ADD COLUMN condition_met_at DATETIME COMMENT '条件满足时间' AFTER condition_status;
