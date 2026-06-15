-- 2026-06-15: 后台深度分析框架 (qa-agent analysis worker)
-- 与入库解耦：材料 parse 完成后由后台异步提取项目/资产关键信息

CREATE TABLE IF NOT EXISTS analysis_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE COMMENT '模板编码，如 project.investment_structure',
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    scope VARCHAR(32) NOT NULL COMMENT 'project|asset|material',
    prompt_template TEXT NOT NULL COMMENT 'LLM 提示词，占位符: {project_name},{project_code},{materials}',
    output_schema JSON NOT NULL COMMENT '期望 JSON 结构说明/JSON Schema',
    builtin TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    max_input_chars INT NOT NULL DEFAULT 30000,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_at_scope_enabled (scope, enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='深度分析模板(可扩展)';

CREATE TABLE IF NOT EXISTS project_asset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    asset_type VARCHAR(32) NOT NULL COMMENT 'credit|collateral|equity|other',
    name VARCHAR(512) NOT NULL COMMENT '资产标识名，如 南安市岭兜建材二厂债权',
    display_name VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    source_template VARCHAR(64) COMMENT '来源分析模板 code',
    metadata_json JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pa_project_type (project_id, asset_type),
    CONSTRAINT fk_pa_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目底层资产';

CREATE TABLE IF NOT EXISTS analysis_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_type VARCHAR(32) NOT NULL DEFAULT 'project_deep' COMMENT 'project_deep|asset_credit',
    project_id BIGINT NOT NULL,
    asset_id BIGINT NULL,
    template_code VARCHAR(64) NULL COMMENT 'NULL=按 scope 跑全部启用模板',
    status VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT 'pending|running|success|failed|cancelled',
    priority INT NOT NULL DEFAULT 100,
    trigger_reason VARCHAR(64) COMMENT 'new_material|stale|manual|bootstrap',
    material_fingerprint VARCHAR(64),
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    last_error TEXT,
    scheduled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME,
    finished_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_aj_status_priority (status, priority, scheduled_at),
    INDEX idx_aj_project_status (project_id, status),
    CONSTRAINT fk_aj_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_aj_asset FOREIGN KEY (asset_id) REFERENCES project_asset(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台分析任务队列';

CREATE TABLE IF NOT EXISTS analysis_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    asset_id BIGINT NULL,
    asset_key BIGINT AS (IFNULL(asset_id, 0)) STORED COMMENT '唯一键辅助列',
    template_code VARCHAR(64) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    result_json JSON NOT NULL,
    summary_text TEXT COMMENT '一句话摘要，便于 Agent 直读',
    confidence DECIMAL(3,2),
    confidence_level VARCHAR(16) COMMENT 'CONFIRMED/AI_INFERRED/PENDING_REVIEW',
    evidence_material_version_id BIGINT,
    evidence_snippet TEXT,
    material_fingerprint VARCHAR(64),
    analyzer_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_snapshot_target (project_id, template_code, asset_key),
    INDEX idx_as_project_template (project_id, template_code),
    CONSTRAINT fk_as_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_as_asset FOREIGN KEY (asset_id) REFERENCES project_asset(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分析结果快照';

CREATE TABLE IF NOT EXISTS project_analysis_state (
    project_id BIGINT PRIMARY KEY,
    material_fingerprint VARCHAR(64),
    last_job_id BIGINT,
    last_status VARCHAR(32) NOT NULL DEFAULT 'never' COMMENT 'never|pending|running|success|failed',
    last_started_at DATETIME,
    last_completed_at DATETIME,
    last_error TEXT,
    snapshot_count INT NOT NULL DEFAULT 0,
    asset_count INT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pas_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目分析进度状态';

-- 内置模板 (可后续在管理端扩展)
INSERT IGNORE INTO analysis_template
    (code, name, description, scope, prompt_template, output_schema, builtin, enabled, sort_order, max_input_chars)
VALUES
(
    'project.investment_structure',
    '项目投资结构',
    '提取投资结构、交易形式、核心资产概述',
    'project',
    '你是投委会档案分析助手。根据以下项目材料，提取项目投资结构与交易形式。\n项目: {project_name} ({project_code})\n\n材料正文:\n{materials}\n\n只输出 JSON，不要 markdown。',
    JSON_OBJECT(
        'type', 'object',
        'fields', JSON_ARRAY(
            'investmentStructure', 'transactionForm', 'coreAssets',
            'financingAmountWan', 'counterparty', 'notes'
        )
    ),
    1, 1, 10, 30000
),
(
    'project.interest_rate_schedule',
    '项目利率历程',
    '提取项目生命周期内利率/固定收益约定及变更',
    'project',
    '你是投委会档案分析助手。从材料中提取与利率、固定收益、回购收益率相关的约定。\n项目: {project_name} ({project_code})\n\n材料正文:\n{materials}\n\n利率可能在不同阶段变化，请列出每次约定。只输出 JSON。',
    JSON_OBJECT(
        'type', 'object',
        'fields', JSON_ARRAY('currentRate', 'rateUnit', 'rateSchedule', 'notes')
    ),
    1, 1, 20, 30000
),
(
    'asset.credit_profile',
    '债权资产画像',
    '提取单笔债权的债务人、担保、抵押、转让、利率、法律状态等',
    'asset',
    '你是不良资产/债权分析助手。针对项目材料中的债权底层资产，逐笔提取关键信息。\n项目: {project_name} ({project_code})\n目标资产: {asset_name}\n\n材料正文:\n{materials}\n\n只输出 JSON。',
    JSON_OBJECT(
        'type', 'object',
        'fields', JSON_ARRAY(
            'assetName', 'debtor', 'guarantors', 'collateral',
            'originalCreditor', 'transferChain', 'principalWan',
            'interestRates', 'startDate', 'legalStatus', 'unsuedDetails', 'notes'
        )
    ),
    1, 1, 30, 35000
),
(
    'project.credit_inventory',
    '项目债权清单',
    '识别项目涉及的全部债权标的，供后续逐笔深度分析',
    'project',
    '你是投委会档案分析助手。列出本项目材料中涉及的全部债权/底层资产名称。\n项目: {project_name} ({project_code})\n\n材料正文:\n{materials}\n\n只输出 JSON。',
    JSON_OBJECT(
        'type', 'object',
        'fields', JSON_ARRAY('credits', 'notes')
    ),
    1, 1, 5, 30000
);
