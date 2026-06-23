package com.agentforge.web.service;

import java.util.Map;

/**
 * 日志查询服务接口。
 */
public interface ILogQueryService {

    Map<String, Object> sqlLogs(String sessionId, int page, int size);

    Map<String, Object> agentLogs(String sessionId, String agentName, int page, int size);

    Map<String, Object> sqlStats();
}
