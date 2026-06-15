-- v1.2+: 多轮会话记住上一轮讨论的债权标的 (用于「这个债权的抵押物」等追问)
ALTER TABLE chat_session_context
    ADD COLUMN last_debt_target VARCHAR(256) NULL
        COMMENT '上一轮确认的债权标的 (如 南安市岭兜建材二厂债权)'
        AFTER project_name;
