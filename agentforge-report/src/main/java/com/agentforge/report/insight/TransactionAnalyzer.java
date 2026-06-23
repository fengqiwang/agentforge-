package com.agentforge.report.insight;

import com.agentforge.framework.cache.ReportCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多维度流水分析。settledate 为 'YYYYMMDD' 字符串格式。
 *
 * <p>维度：time(按月) / geo(城市) / merchant(custype) / amount(金额区间) / type(transtype) / activity(按日笔数)
 *
 * <p>Week 11：结果走 Redis 缓存（TTL 5min），相同维度+时间范围重复查询命中缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionAnalyzer {

    private final JdbcTemplate jdbcTemplate;
    private final TrendDetector trendDetector;
    private final AnomalyDetector anomalyDetector;
    private final ReportCacheService reportCacheService;

    public AnalysisResult analyze(String dimension, String start, String end) {
        // Week 11：先查缓存
        String cacheKey = dimension + "|" + (start == null ? "" : start) + "|" + (end == null ? "" : end);
        AnalysisResult cached = reportCacheService.get("analysis", cacheKey, AnalysisResult.class);
        if (cached != null) {
            log.info("[Insight] {} 维度命中缓存", dimension);
            return cached;
        }

        AnalysisResult result = compute(dimension, start, end);
        reportCacheService.put("analysis", cacheKey, result);
        return result;
    }

    /** 实际计算（缓存未命中时执行）。 */
    private AnalysisResult compute(String dimension, String start, String end) {
        AnalysisResult result = new AnalysisResult();
        result.setDimension(dimension);

        List<AnalysisResult.DataPoint> points = switch (dimension) {
            case "time"     -> analyzeByTime(start, end);
            case "geo"      -> analyzeByGeo(start, end);
            case "merchant" -> analyzeByMerchant(start, end);
            case "amount"   -> analyzeByAmount(start, end);
            case "type"     -> analyzeByType(start, end);
            case "activity" -> analyzeByActivity(start, end);
            default -> throw new IllegalArgumentException("未知维度: " + dimension);
        };
        result.setDataPoints(points);

        // 时间序列维度做趋势/异常检测
        if ("time".equals(dimension) || "activity".equals(dimension)) {
            result.setTrends(trendDetector.detect(points));
            result.setAnomalies(anomalyDetector.detect(points));
        }

        result.setSummary(buildSummary(dimension, points));
        log.info("[Insight] {} 维度分析完成: {} 个数据点, 趋势{} 异常{}",
                dimension, points.size(),
                result.getTrends() != null ? result.getTrends().size() : 0,
                result.getAnomalies() != null ? result.getAnomalies().size() : 0);
        return result;
    }

    /** 时间维度：按月聚合交易额 + 笔数（收银宝+收付通 UNION，settledate DIV 100 取月）。 */
    private List<AnalysisResult.DataPoint> analyzeByTime(String start, String end) {
        String where = buildDateRange(start, end);
        // 收银宝 + 收付通（排除 4 类 transtype）合并后按月聚合
        String sql = "SELECT ym, SUM(amount) AS amount, SUM(cnt) AS cnt FROM (" +
                "SELECT settledate DIV 100 AS ym, SUM(tranamt) AS amount, COUNT(*) AS cnt FROM syb_transuminfor " + where +
                " GROUP BY settledate DIV 100" +
                " UNION ALL SELECT settledate DIV 100, SUM(tranamt), COUNT(*) FROM tlt_transuminfor " + where +
                " AND transtype NOT IN ('结算-T+0代收付款','结算-代收付款','结算-代付失败退款','提现') GROUP BY settledate DIV 100" +
                ") tmp GROUP BY ym ORDER BY ym";
        return jdbcTemplate.query(sql, (rs, n) -> {
            AnalysisResult.DataPoint p = new AnalysisResult.DataPoint();
            p.setLabel(rs.getString("ym"));
            p.setValue(rs.getDouble("amount"));
            p.setCount(rs.getLong("cnt"));
            return p;
        });
    }

    /** 地理维度：各城市交易额 + 占比。 */
    private List<AnalysisResult.DataPoint> analyzeByGeo(String start, String end) {
        String where = buildDateRange(start, end, "t.");
        String sql = "SELECT m.city, SUM(t.tranamt) AS amount, COUNT(*) AS cnt " +
                "FROM syb_transuminfor t INNER JOIN syb_merchant m ON t.cusid = m.cusid " +
                where + " GROUP BY m.city ORDER BY amount DESC";
        List<AnalysisResult.DataPoint> points = jdbcTemplate.query(sql, (rs, n) -> {
            AnalysisResult.DataPoint p = new AnalysisResult.DataPoint();
            p.setLabel(rs.getString("city"));
            p.setValue(rs.getDouble("amount"));
            p.setCount(rs.getLong("cnt"));
            return p;
        });
        fillRatio(points);
        return points;
    }

    /** 商户类型维度：各 custype 占比。 */
    private List<AnalysisResult.DataPoint> analyzeByMerchant(String start, String end) {
        String where = buildDateRange(start, end, "t.");
        String sql = "SELECT m.custype, SUM(t.tranamt) AS amount, COUNT(*) AS cnt " +
                "FROM syb_transuminfor t INNER JOIN syb_merchant m ON t.cusid = m.cusid " +
                where + " GROUP BY m.custype ORDER BY amount DESC";
        List<AnalysisResult.DataPoint> points = jdbcTemplate.query(sql, (rs, n) -> {
            AnalysisResult.DataPoint p = new AnalysisResult.DataPoint();
            p.setLabel(rs.getString("custype"));
            p.setValue(rs.getDouble("amount"));
            p.setCount(rs.getLong("cnt"));
            return p;
        });
        fillRatio(points);
        return points;
    }

    /** 金额维度：区间分布。 */
    private List<AnalysisResult.DataPoint> analyzeByAmount(String start, String end) {
        String where = buildDateRange(start, end);
        String sql = "SELECT " +
                "  CASE WHEN tranamt < 100 THEN '<100' " +
                "       WHEN tranamt < 1000 THEN '100-1K' " +
                "       WHEN tranamt < 10000 THEN '1K-10K' " +
                "       ELSE '>10K' END AS bucket, " +
                "  COUNT(*) AS cnt, SUM(tranamt) AS amount " +
                "FROM syb_transuminfor " + where +
                " GROUP BY bucket ORDER BY FIELD(bucket,'<100','100-1K','1K-10K','>10K')";
        List<AnalysisResult.DataPoint> points = jdbcTemplate.query(sql, (rs, n) -> {
            AnalysisResult.DataPoint p = new AnalysisResult.DataPoint();
            p.setLabel(rs.getString("bucket"));
            p.setValue(rs.getDouble("amount"));
            p.setCount(rs.getLong("cnt"));
            return p;
        });
        fillRatio(points);
        return points;
    }

    /** 交易类型维度：transtype 分布。 */
    private List<AnalysisResult.DataPoint> analyzeByType(String start, String end) {
        String where = buildDateRange(start, end);
        String sql = "SELECT transtype, SUM(tranamt) AS amount, COUNT(*) AS cnt " +
                "FROM syb_transuminfor " + where + " GROUP BY transtype ORDER BY amount DESC";
        List<AnalysisResult.DataPoint> points = jdbcTemplate.query(sql, (rs, n) -> {
            AnalysisResult.DataPoint p = new AnalysisResult.DataPoint();
            p.setLabel(rs.getString("transtype"));
            p.setValue(rs.getDouble("amount"));
            p.setCount(rs.getLong("cnt"));
            return p;
        });
        fillRatio(points);
        return points;
    }

    /** 活跃度维度：按日聚合笔数 + 活跃商户数。 */
    private List<AnalysisResult.DataPoint> analyzeByActivity(String start, String end) {
        String where = buildDateRange(start, end);
        String sql = "SELECT settledate AS d, COUNT(*) AS cnt, COUNT(DISTINCT cusid) AS merchants " +
                "FROM syb_transuminfor " + where + " GROUP BY settledate ORDER BY settledate";
        return jdbcTemplate.query(sql, (rs, n) -> {
            AnalysisResult.DataPoint p = new AnalysisResult.DataPoint();
            p.setLabel(rs.getString("d"));
            p.setValue(rs.getDouble("cnt"));
            p.setCount(rs.getLong("merchants"));
            return p;
        });
    }

    /** 给每个点填充占比（基于 value 总和）。 */
    private void fillRatio(List<AnalysisResult.DataPoint> points) {
        double total = points.stream().mapToDouble(AnalysisResult.DataPoint::getValue).sum();
        if (total <= 0) return;
        points.forEach(p -> p.setRatio(p.getValue() / total));
    }

    /** 构造日期范围 WHERE。settledate 为 'YYYYMMDD' 字符串。 */
    private String buildDateRange(String start, String end) {
        return buildDateRange(start, end, "");
    }

    private String buildDateRange(String start, String end, String alias) {
        List<String> conds = new ArrayList<>();
        if (start != null && !start.isBlank()) {
            conds.add(alias + "settledate >= '" + start.replace("-", "") + "'");
        }
        if (end != null && !end.isBlank()) {
            conds.add(alias + "settledate <= '" + end.replace("-", "") + "'");
        }
        return conds.isEmpty() ? "WHERE 1=1" : "WHERE " + String.join(" AND ", conds);
    }

    /** 规则摘要：TOP3 + 占比。 */
    private String buildSummary(String dimension, List<AnalysisResult.DataPoint> points) {
        if (points == null || points.isEmpty()) return "无数据";
        String dimName = Map.of("time", "时间", "geo", "地理", "merchant", "商户类型",
                "amount", "金额", "type", "交易类型", "activity", "活跃度").getOrDefault(dimension, dimension);
        StringBuilder sb = new StringBuilder(dimName).append("维度：");
        int top = Math.min(points.size(), 3);
        for (int i = 0; i < top; i++) {
            AnalysisResult.DataPoint p = points.get(i);
            sb.append(p.getLabel()).append("(").append(formatValue(p.getValue()));
            if (p.getRatio() != null) sb.append(",").append(String.format("%.1f%%", p.getRatio() * 100));
            sb.append(")");
            if (i < top - 1) sb.append("、");
        }
        return sb.toString();
    }

    private String formatValue(double v) {
        if (v >= 1e8) return String.format("%.2f亿", v / 1e8);
        if (v >= 1e4) return String.format("%.2f万", v / 1e4);
        return String.format("%.0f", v);
    }
}
