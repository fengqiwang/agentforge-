package com.agentforge.report.insight;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 异常检测：偏离均值 > 2σ 的点标记为异常（Z-score 法）。
 * 适用于 time / activity 等时间序列维度。
 */
@Component
public class AnomalyDetector {

    private static final double Z_THRESHOLD = 2.0;

    public List<AnalysisResult.AnomalyInfo> detect(List<AnalysisResult.DataPoint> points) {
        List<AnalysisResult.AnomalyInfo> anomalies = new ArrayList<>();
        if (points == null || points.size() < 3) return anomalies;

        double mean = points.stream().mapToDouble(AnalysisResult.DataPoint::getValue).average().orElse(0);
        double variance = points.stream()
                .mapToDouble(p -> Math.pow(p.getValue() - mean, 2))
                .average().orElse(0);
        double std = Math.sqrt(variance);
        if (std <= 0) return anomalies;

        for (AnalysisResult.DataPoint p : points) {
            double z = (p.getValue() - mean) / std;
            if (Math.abs(z) >= Z_THRESHOLD) {
                AnalysisResult.AnomalyInfo a = new AnalysisResult.AnomalyInfo();
                a.setType(z > 0 ? "SPIKE" : "DROP");
                a.setLabel(p.getLabel());
                a.setValue(p.getValue());
                a.setExpected(mean);
                a.setZScore(z);
                a.setDescription(String.format("%s 偏离均值 %.2fσ（值 %s，期望 %s）",
                        p.getLabel(), z, format(p.getValue()), format(mean)));
                anomalies.add(a);
            }
        }
        return anomalies;
    }

    private String format(double v) {
        if (v >= 1e4) return String.format("%.2f万", v / 1e4);
        return String.format("%.2f", v);
    }
}
