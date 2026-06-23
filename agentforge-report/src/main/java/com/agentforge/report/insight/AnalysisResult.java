package com.agentforge.report.insight;

import lombok.Data;
import java.util.List;

/**
 * 单维度流水分析结果。
 * 由 {@link TransactionAnalyzer} 计算，{@link TrendDetector}/{@link AnomalyDetector} 补充趋势与异常。
 */
@Data
public class AnalysisResult {

    /** 维度：time / geo / merchant / amount / type / activity */
    private String dimension;

    /** 离散数据点（如每月交易额、每城市占比） */
    private List<DataPoint> dataPoints;

    /** 检测到的趋势 */
    private List<TrendInfo> trends;

    /** 检测到的异常 */
    private List<AnomalyInfo> anomalies;

    /** 规则生成的文字摘要 */
    private String summary;

    @Data
    public static class DataPoint {
        private String label;        // "2026-03" / "南昌市" / "100-1000元"
        private double value;        // 金额或笔数
        private Long count;          // 记录数（可选）
        private Double ratio;        // 占比（0~1，可选）
    }

    @Data
    public static class TrendInfo {
        private String type;         // GROWTH / DECLINE / PERIODIC
        private String description;
        private Double rate;
    }

    @Data
    public static class AnomalyInfo {
        private String type;         // SPIKE / DROP / LARGE_AMOUNT / GEO_SHIFT
        private String label;
        private double value;
        private double expected;
        private double zScore;
        private String description;
    }
}
