CREATE TABLE IF NOT EXISTS af_sql_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64),
    sql_text TEXT NOT NULL,
    tables_used JSON,
    result_count INT,
    duration_ms INT,
    is_valid BOOLEAN,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id)
) COMMENT 'SQL执行记录';
