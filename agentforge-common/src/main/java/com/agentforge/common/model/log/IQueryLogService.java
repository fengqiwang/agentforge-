package com.agentforge.common.model.log;

import java.util.List;

/**
 * SQL 执行日志服务接口
 */
public interface IQueryLogService {

    /**
     * 异步记录 SQL 执行日志
     *
     * @param sessionId  会话ID
     * @param sqlText    执行的 SQL
     * @param tablesUsed 涉及的表（List → JSON）
     * @param resultCount 返回行数
     * @param durationMs 执行耗时（毫秒）
     * @param isValid    是否通过安全校验
     * @param level      生成级别 TEMPLATE/FEW_SHOT/LLM
     */
    void log(String sessionId, String sqlText, List<String> tablesUsed,
             int resultCount, long durationMs, boolean isValid, String level);

    /**
     * 简化版：只记录核心字段
     */
    void log(String sessionId, String sqlText, int resultCount,
             long durationMs, boolean isValid);
}
