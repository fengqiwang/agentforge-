CREATE TABLE IF NOT EXISTS af_agent_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64),
    agent_name VARCHAR(50) NOT NULL,
    input_text TEXT,
    output_text TEXT,
    duration_ms INT,
    token_used INT,
    status VARCHAR(20) COMMENT 'success/failed/timeout',
    error_msg TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_agent (agent_name)
) COMMENT 'Agent执行日志';
