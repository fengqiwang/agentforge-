package com.agentforge.report.insight;

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
 * 洞察服务：跑分析任务 → 调 AI 生成洞察 → 落库 af_market_insight。
 * 使用 typed {@link AnalysisResult} 避免反序列化丢类型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private final JdbcTemplate jdbcTemplate;
    private final InsightGenerator generator;
    private final AnalysisService analysisService;
    private final ObjectMapper objectMapper;

    private static final List<String> ALL_DIMS =
            List.of("time", "geo", "merchant", "amount", "type", "activity");

    /** 生成洞察：先跑全维度分析，再调 AI。 */
    public Long generate(String periodType, String start, String end) {
        AnalysisService.AnalysisOutcome outcome =
                analysisService.createTaskWithResults("insight-" + periodType, ALL_DIMS, start, end);

        MarketInsight insight = generator.generate(periodType, start, end,
                outcome.taskId(), outcome.results());
        return persist(insight);
    }

    private Long persist(MarketInsight insight) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO af_market_insight (period_type, period_start, period_end, analysis_task_id, trends, anomalies, opportunities, summary) VALUES (?,?,?,?,?,?,?,?)",
                    insight.getPeriodType(), insight.getPeriodStart(), insight.getPeriodEnd(),
                    insight.getAnalysisTaskId(),
                    toJson(insight.getTrends()), toJson(insight.getAnomalies()),
                    toJson(insight.getOpportunities()), insight.getSummary());
            Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            log.info("洞察已存储 id={} period={}/{}", id, insight.getPeriodType(), insight.getPeriodStart());
            return id;
        } catch (Exception e) {
            log.error("洞察存储失败", e);
            return null;
        }
    }

    public Map<String, Object> getById(Long id) {
        return jdbcTemplate.queryForObject("SELECT * FROM af_market_insight WHERE id=?", (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("periodType", rs.getString("period_type"));
            m.put("periodStart", rs.getString("period_start"));
            m.put("periodEnd", rs.getString("period_end"));
            m.put("analysisTaskId", rs.getLong("analysis_task_id"));
            m.put("trends", fromJson(rs.getString("trends")));
            m.put("anomalies", fromJson(rs.getString("anomalies")));
            m.put("opportunities", fromJson(rs.getString("opportunities")));
            m.put("summary", rs.getString("summary"));
            m.put("status", rs.getString("status"));
            Timestamp ts = rs.getTimestamp("created_at");
            m.put("createdAt", ts == null ? null : ts.toLocalDateTime().toString());
            return m;
        }, id);
    }

    public List<Map<String, Object>> list() {
        return jdbcTemplate.query(
                "SELECT id, period_type, period_start, period_end, summary, created_at FROM af_market_insight ORDER BY id DESC",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("periodType", rs.getString("period_type"));
                    m.put("periodStart", rs.getString("period_start"));
                    m.put("periodEnd", rs.getString("period_end"));
                    m.put("summary", rs.getString("summary"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    m.put("createdAt", ts == null ? null : ts.toLocalDateTime().toString());
                    return m;
                });
    }

    private String toJson(Object o) {
        if (o == null) return null;
        try { return objectMapper.writeValueAsString(o); }
        catch (Exception e) { return null; }
    }

    private Object fromJson(String j) {
        if (j == null) return null;
        try { return objectMapper.readValue(j, Object.class); }
        catch (Exception e) { return null; }
    }
}
