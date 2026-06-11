-- MOD-01 / RI-22: 置信度 3 级 (CONFIRMED / AI_INFERRED / PENDING_REVIEW)
-- 依赖: project 表已存在

USE archive_db;

-- v1.1 基础表 (RI-6/RI-7, 若尚未创建)
CREATE TABLE IF NOT EXISTS project_fact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    fact_type VARCHAR(64) NOT NULL COMMENT 'mortgage/guarantor/settlement/milestone/risk/decision/transaction',
    fact_value TEXT,
    confidence DECIMAL(3,2) COMMENT 'LLM 置信度 0-1',
    evidence_material_id BIGINT COMMENT '证据材料 ID',
    evidence_snippet TEXT COMMENT '证据原文摘录',
    status VARCHAR(32) DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pf_project_type (project_id, fact_type),
    CONSTRAINT fk_pf_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目关键事实';

CREATE TABLE IF NOT EXISTS project_fact_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    fact_type VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL COMMENT 'INSERT/UPDATE/DELETE/ROLLBACK',
    fact_value TEXT,
    evidence TEXT,
    confidence DECIMAL(3,2),
    created_by BIGINT COMMENT '操作者 user_id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    INDEX idx_pfe_project_type (project_id, fact_type),
    CONSTRAINT fk_pfe_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关键事实事件流(INSERT-only)';

ALTER TABLE project_fact
    ADD COLUMN confidence_level VARCHAR(16) COMMENT 'CONFIRMED/AI_INFERRED/PENDING_REVIEW' AFTER confidence;

ALTER TABLE project_fact_event
    ADD COLUMN confidence_level VARCHAR(16) COMMENT 'CONFIRMED/AI_INFERRED/PENDING_REVIEW' AFTER confidence;

UPDATE project_fact SET confidence_level = CASE
    WHEN confidence >= 0.85 THEN 'CONFIRMED'
    WHEN confidence >= 0.60 THEN 'AI_INFERRED'
    ELSE 'PENDING_REVIEW'
END WHERE confidence_level IS NULL;

UPDATE project_fact_event SET confidence_level = CASE
    WHEN confidence >= 0.85 THEN 'CONFIRMED'
    WHEN confidence >= 0.60 THEN 'AI_INFERRED'
    WHEN confidence IS NOT NULL THEN 'PENDING_REVIEW'
    ELSE 'PENDING_REVIEW'
END WHERE confidence_level IS NULL;
