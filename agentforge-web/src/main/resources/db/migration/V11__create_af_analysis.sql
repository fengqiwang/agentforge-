-- Week 9 流水分析引擎：分析任务表
CREATE TABLE IF NOT EXISTS af_analysis_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '分析任务名称',
    dimensions JSON COMMENT '分析维度["time","geo","merchant"]',
    date_range_start VARCHAR(20) COMMENT '开始日期 YYYYMMDD',
    date_range_end VARCHAR(20) COMMENT '结束日期 YYYYMMDD',
    status VARCHAR(20) DEFAULT 'RUNNING' COMMENT 'RUNNING/COMPLETED/FAILED',
    result JSON COMMENT '分析结果',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT '分析任务';
