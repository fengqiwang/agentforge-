package com.agentforge.common.model.log;

/**
 * Agent 执行日志服务接口
 */
public interface IAgentLogService {

    /**
     * 异步记录 Agent 执行日志
     */
    void log(String sessionId, String agentName, String input,
             String output, long durationMs, Integer tokenUsed,
             String status, String errorMsg);

    /**
     * 记录成功
     */
    void logSuccess(String sessionId, String agentName,
                    String input, String output,
                    long durationMs, Integer tokenUsed);

    /**
     * 记录失败
     */
    void logFailure(String sessionId, String agentName,
                    String input, long durationMs, String errorMsg);
}
