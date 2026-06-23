package com.agentforge.report.insight.impl;

import com.agentforge.report.insight.IAnalysisService;
import com.agentforge.report.insight.AnalysisResult;
import com.agentforge.report.insight.TransactionAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分析任务管理：创建任务 → 逐维度分析 → 汇总结果落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements IAnalysisService {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionAnalyzer analyzer;
    private final ObjectMapper objectMapper;

    /** 创建并执行分析任务，返回 typed 结果。 */
    @Override
    public AnalysisOutcome createTaskWithResults(String name, List<String> dimensions, String start, String end) {
        jdbcTemplate.update(
                "INSERT INTO af_analysis_task (name, dimensions, date_range_start, date_range_end, status) VALUES (?,?,?,?,?)",
                name, toJson(dimensions), start, end, "RUNNING");
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        Map<String, AnalysisResult> results = new LinkedHashMap<>();
        try {
            for (String dim : dimensions) {
                results.put(dim, analyzer.analyze(dim, start, end));
            }
            jdbcTemplate.update(
                    "UPDATE af_analysis_task SET status='COMPLETED', result=? WHERE id=?",
                    toJson(results), id);
            log.info("分析任务完成 id={} dims={}", id, dimensions);
        } catch (Exception e) {
            log.error("分析任务失败 id={}", id, e);
            jdbcTemplate.update("UPDATE af_analysis_task SET status='FAILED' WHERE id=?", id);
        }
        return new AnalysisOutcome(id, results);
    }

    /** 创建并执行分析任务，仅返回任务 id（向后兼容）。 */
    @Override
    public Long createTask(String name, List<String> dimensions, String start, String end) {
        return createTaskWithResults(name, dimensions, start, end).taskId();
    }

    @Override
    public Map<String, Object> getById(Long id) {
        return jdbcTemplate.queryForObject("SELECT * FROM af_analysis_task WHERE id=?", (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("dimensions", fromJson(rs.getString("dimensions")));
            m.put("dateRangeStart", rs.getString("date_range_start"));
            m.put("dateRangeEnd", rs.getString("date_range_end"));
            m.put("status", rs.getString("status"));
            m.put("result", fromJson(rs.getString("result")));
            Timestamp ts = rs.getTimestamp("created_at");
            m.put("createdAt", ts == null ? null : ts.toLocalDateTime().toString());
            return m;
        }, id);
    }

    @Override
    public List<Map<String, Object>> list() {
        return jdbcTemplate.query("SELECT id, name, status, created_at FROM af_analysis_task ORDER BY id DESC",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("name", rs.getString("name"));
                    m.put("status", rs.getString("status"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    m.put("createdAt", ts == null ? null : ts.toLocalDateTime().toString());
                    return m;
                });
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            log.error("JSON序列化失败", e);
            throw new IllegalStateException("JSON序列化失败: " + e.getMessage(), e);
        }
    }

    private Object fromJson(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            log.warn("JSON反序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
