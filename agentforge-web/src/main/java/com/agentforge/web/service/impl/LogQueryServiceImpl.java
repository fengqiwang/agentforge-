package com.agentforge.web.service.impl;

import com.agentforge.web.service.ILogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志查询服务 — LogController 的业务逻辑层。
 */
@Service
@RequiredArgsConstructor
public class LogQueryServiceImpl implements ILogQueryService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> sqlLogs(String sessionId, int page, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, session_id, sql_text, tables_used, result_count, " +
                "duration_ms, is_valid, created_at FROM af_sql_log WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (sessionId != null && !sessionId.isBlank()) {
            sql.append(" AND session_id = ?");
            params.add(sessionId);
        }

        int total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM af_sql_log WHERE 1=1"
                        + (sessionId != null && !sessionId.isBlank() ? " AND session_id = ?" : ""),
                Integer.class, params.toArray());

        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("logs", logs);
        return result;
    }

    @Override
    public Map<String, Object> agentLogs(String sessionId, String agentName, int page, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, session_id, agent_name, input_text AS input, output_text AS output, " +
                "duration_ms, token_used, status, error_msg, created_at " +
                "FROM af_agent_log WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (sessionId != null && !sessionId.isBlank()) {
            sql.append(" AND session_id = ?");
            params.add(sessionId);
        }
        if (agentName != null && !agentName.isBlank()) {
            sql.append(" AND agent_name = ?");
            params.add(agentName);
        }

        List<Object> countParams = new ArrayList<>(params);
        int total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM af_agent_log WHERE 1=1"
                        + buildCountWhere(sessionId, agentName),
                Integer.class, countParams.toArray());

        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("logs", logs);
        return result;
    }

    @Override
    public Map<String, Object> sqlStats() {
        Integer totalQueries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM af_sql_log", Integer.class);
        Integer successCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM af_sql_log WHERE is_valid = true", Integer.class);
        Double avgDuration = jdbcTemplate.queryForObject(
                "SELECT AVG(duration_ms) FROM af_sql_log WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)",
                Double.class);
        Integer todayCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM af_sql_log WHERE DATE(created_at) = CURDATE()",
                Integer.class);

        return Map.of(
                "totalQueries", totalQueries != null ? totalQueries : 0,
                "successCount", successCount != null ? successCount : 0,
                "avgDurationMs", avgDuration != null ? avgDuration.intValue() : 0,
                "todayCount", todayCount != null ? todayCount : 0
        );
    }

    private String buildCountWhere(String sessionId, String agentName) {
        StringBuilder sb = new StringBuilder();
        if (sessionId != null && !sessionId.isBlank()) sb.append(" AND session_id = ?");
        if (agentName != null && !agentName.isBlank()) sb.append(" AND agent_name = ?");
        return sb.toString();
    }
}
