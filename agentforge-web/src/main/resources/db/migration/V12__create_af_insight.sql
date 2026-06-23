-- Week 10 v2 市场洞察：洞察表
CREATE TABLE IF NOT EXISTS af_market_insight (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_type VARCHAR(10) NOT NULL COMMENT 'WEEKLY/MONTHLY',
    period_start VARCHAR(20) NOT NULL,
    period_end VARCHAR(20) NOT NULL,
    analysis_task_id BIGINT COMMENT '关联的分析任务',
    trends JSON COMMENT '趋势发现',
    anomalies JSON COMMENT '异常告警',
    opportunities JSON COMMENT '机会点',
    summary TEXT COMMENT 'AI总结',
    status VARCHAR(20) DEFAULT 'GENERATED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_period (period_type, period_start)
) COMMENT '市场洞察';
