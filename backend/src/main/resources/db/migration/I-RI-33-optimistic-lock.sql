-- MOD-01 / RI-33: 乐观锁 (project/proposal/material 已在 I-RI-31 加 version, 此处确保默认值)
USE archive_db;

ALTER TABLE project MODIFY COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本';
ALTER TABLE proposal MODIFY COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本';
ALTER TABLE material MODIFY COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本';
