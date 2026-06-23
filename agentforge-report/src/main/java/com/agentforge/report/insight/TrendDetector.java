package com.agentforge.report.insight;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 趋势检测（纯规则）。
 * <ul>
 *   <li>连续 3 个点同向变化且幅度 > 10% → GROWTH / DECLINE</li>
 *   <li>首尾波动 < 5% 且无明显趋势 → PERIODIC</li>
 * </ul>
 */
@Component
public class TrendDetector {

    private static final double TREND_THRESHOLD = 0.10;

    public List<AnalysisResult.TrendInfo> detect(List<AnalysisResult.DataPoint> points) {
        List<AnalysisResult.TrendInfo> trends = new ArrayList<>();
        if (points == null || points.size() < 4) return trends;

        int n = points.size();
        int upCount = 0, downCount = 0;
        double lastRate = 0;
        for (int i = n - 3; i < n; i++) {
            double prev = points.get(i - 1).getValue();
            double curr = points.get(i).getValue();
            if (prev <= 0) continue;
            double rate = (curr - prev) / prev;
            lastRate = rate;
            if (rate > TREND_THRESHOLD) upCount++;
            else if (rate < -TREND_THRESHOLD) downCount++;
        }

        if (upCount >= 3) {
            AnalysisResult.TrendInfo t = new AnalysisResult.TrendInfo();
            t.setType("GROWTH");
            t.setDescription(String.format("连续3期增长，最近一期环比+%.1f%%", lastRate * 100));
            t.setRate(lastRate);
            trends.add(t);
        } else if (downCount >= 3) {
            AnalysisResult.TrendInfo t = new AnalysisResult.TrendInfo();
            t.setType("DECLINE");
            t.setDescription(String.format("连续3期下降，最近一期环比%.1f%%", lastRate * 100));
            t.setRate(lastRate);
            trends.add(t);
        }

        double first = points.get(0).getValue();
        double last = points.get(n - 1).getValue();
        if (first > 0 && Math.abs(last - first) / first < 0.05 && trends.isEmpty()) {
            AnalysisResult.TrendInfo t = new AnalysisResult.TrendInfo();
            t.setType("PERIODIC");
            t.setDescription("首尾波动<5%，呈稳定/周期性");
            t.setRate((last - first) / first);
            trends.add(t);
        }
        return trends;
    }
}
