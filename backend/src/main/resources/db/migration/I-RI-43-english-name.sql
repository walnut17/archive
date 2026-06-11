-- MOD-01 / RI-43: 业务术语英文对照
USE archive_db;

ALTER TABLE business_term
    ADD COLUMN english_name VARCHAR(128) COMMENT '英文名称(可选)' AFTER name;
