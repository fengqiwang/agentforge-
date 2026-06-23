package com.agentforge.report.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 洞察生成器：把 typed {@link AnalysisResult} 喂给 LLM，生成 {@link MarketInsight}。
 * LLM 不可用或解析失败时降级为规则摘要（始终有结构化产出）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsightGenerator {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    private static final String PROMPT = """
            你是市场分析专家。根据以下交易数据分析结果，生成市场洞察报告。

            分析数据：
            {{analysisResult}}

            要求：
            1. trends：发现3-5个关键趋势，每个含 finding（发现）、suggestion（建议）、confidence（置信度0-1）
            2. anomalies：列出异常事件，按 HIGH/MEDIUM/LOW 分级
            3. opportunities：发现2-3个市场机会，含 suggestion 和 targetAudience（市场部/运营部）
            4. summary：200字以内中文总结

            严格返回纯JSON，不要markdown代码块：
            {"trends":[{"dimension":"geo","finding":"...","suggestion":"...","confidence":0.85}],"anomalies":[{"dimension":"time","finding":"...","severity":"MEDIUM"}],"opportunities":[{"finding":"...","suggestion":"...","targetAudience":"市场部"}],"summary":"..."}
            """;

    /** 生成洞察。analysis 为 typed {@link AnalysisResult} 维度映射。 */
    public MarketInsight generate(String periodType, String start, String end,
                                  Long analysisTaskId, Map<String, AnalysisResult> analysis) {
        String dataJson = compressAnalysis(analysis);
        String prompt = PROMPT.replace("{{analysisResult}}", dataJson);

        MarketInsight insight = new MarketInsight();
        insight.setPeriodType(periodType);
        insight.setPeriodStart(start);
        insight.setPeriodEnd(end);
        insight.setAnalysisTaskId(analysisTaskId);

        String response;
        try {
            response = chatModel.chat(prompt);
        } catch (Exception e) {
            log.error("洞察 LLM 调用失败，降级: {}", e.getMessage());
            return ruleBasedFallback(insight, analysis, "LLM 调用失败: " + e.getMessage());
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(cleanJson(response), Map.class);
            insight.setTrends(convertList(map.get("trends"), MarketInsight.TrendFinding.class));
            insight.setAnomalies(convertList(map.get("anomalies"), MarketInsight.AnomalyFinding.class));
            insight.setOpportunities(convertList(map.get("opportunities"), MarketInsight.Opportunity.class));
            insight.setSummary((String) map.get("summary"));
            log.info("洞察生成完成: trends={} anomalies={} opportunities={}",
                    size(insight.getTrends()), size(insight.getAnomalies()), size(insight.getOpportunities()));
            return insight;
        } catch (Exception e) {
            log.warn("洞察 JSON 解析失败，降级: {}", e.getMessage());
            return ruleBasedFallback(insight, analysis, response);
        }
    }

    private <T> List<T> convertList(Object src, Class<T> clazz) {
        if (src == null) return new ArrayList<>();
        return objectMapper.convertValue(src,
                objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
    }

    private int size(List<?> l) {
        return l == null ? 0 : l.size();
    }

    /** 把 AnalysisResult 压缩为 LLM 友好文本（每个维度 summary + TOP5 数据点 + 异常数）。 */
    private String compressAnalysis(Map<String, AnalysisResult> analysis) {
        if (analysis == null || analysis.isEmpty()) return "（无分析数据）";
        StringBuilder sb = new StringBuilder();
        for (var e : analysis.entrySet()) {
            AnalysisResult r = e.getValue();
            if (r == null) continue;
            sb.append("【").append(e.getKey()).append("】").append(r.getSummary()).append("\n");
            if (r.getAnomalies() != null && !r.getAnomalies().isEmpty()) {
                sb.append("  异常: ").append(r.getAnomalies().size()).append("处\n");
            }
            int n = Math.min(r.getDataPoints() != null ? r.getDataPoints().size() : 0, 5);
            for (int i = 0; i < n; i++) {
                AnalysisResult.DataPoint p = r.getDataPoints().get(i);
                sb.append("  - ").append(p.getLabel()).append(": ").append((long) p.getValue());
                if (p.getRatio() != null) {
                    sb.append(" (").append(String.format("%.1f%%", p.getRatio() * 100)).append(")");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /** 降级：从已有 AnalysisResult 的异常/趋势直接搬运，保证有产出。 */
    private MarketInsight ruleBasedFallback(MarketInsight insight, Map<String, AnalysisResult> analysis, String raw) {
        insight.setSummary(raw == null || raw.isBlank() ? "洞察生成降级：见原始分析摘要" : raw);
        List<MarketInsight.AnomalyFinding> anomalies = new ArrayList<>();
        List<MarketInsight.TrendFinding> trends = new ArrayList<>();
        if (analysis != null) {
            for (var e : analysis.entrySet()) {
                AnalysisResult r = e.getValue();
                if (r == null) continue;
                if (r.getAnomalies() != null) {
                    for (var a : r.getAnomalies()) {
                        MarketInsight.AnomalyFinding af = new MarketInsight.AnomalyFinding();
                        af.setDimension(e.getKey());
                        af.setFinding(a.getDescription());
                        af.setSeverity(Math.abs(a.getZScore()) > 3 ? "HIGH" : "MEDIUM");
                        anomalies.add(af);
                    }
                }
                if (r.getTrends() != null) {
                    for (var t : r.getTrends()) {
                        MarketInsight.TrendFinding tf = new MarketInsight.TrendFinding();
                        tf.setDimension(e.getKey());
                        tf.setFinding(t.getDescription());
                        tf.setConfidence(0.7);
                        trends.add(tf);
                    }
                }
            }
        }
        insight.setAnomalies(anomalies);
        insight.setTrends(trends);
        return insight;
    }

    private String cleanJson(String s) {
        if (s == null) return "{}";
        String c = s.trim();
        if (c.startsWith("```json")) c = c.substring(7);
        else if (c.startsWith("```")) c = c.substring(3);
        if (c.endsWith("```")) c = c.substring(0, c.length() - 3);
        return c.trim();
    }
}
