package com.agentforge.report.insight;

import lombok.Data;
import java.util.List;

/**
 * AI 市场洞察报告。
 * 由 {@link InsightGenerator} 基于 {@link AnalysisResult} 生成。
 */
@Data
public class MarketInsight {

    private String periodType;        // WEEKLY / MONTHLY
    private String periodStart;
    private String periodEnd;
    private Long analysisTaskId;

    private List<TrendFinding> trends;
    private List<AnomalyFinding> anomalies;
    private List<Opportunity> opportunities;
    private String summary;

    @Data
    public static class TrendFinding {
        private String dimension;
        private String finding;
        private String suggestion;
        private Double confidence;     // 0~1
    }

    @Data
    public static class AnomalyFinding {
        private String dimension;
        private String finding;
        private String severity;       // HIGH / MEDIUM / LOW
    }

    @Data
    public static class Opportunity {
        private String finding;
        private String suggestion;
        private String targetAudience; // 市场部 / 运营部
    }
}
