package com.agentforge.report.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 分析生成器：把 ReportExecutor 产出的查询结果送入 LLM，生成结构化业务分析。
 *
 * <p>核心职责：
 * <ol>
 *   <li>{@link #buildSummary} 把原始查询结果压缩为「名称 + 记录数 + 前5行样本 + 数值列统计」，
 *       避免 token 爆炸。</li>
 *   <li>用严格 few-shot prompt 要求 LLM 只返回 JSON。</li>
 *   <li>{@link #cleanJson} 剥离 DeepSeek 常见的 ```json 代码块包裹。</li>
 *   <li>解析失败时降级为纯文本分析（保证前端始终有内容展示）。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnalysisGenerator {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    private static final String ANALYSIS_PROMPT = """
            你是一个业务数据分析专家。根据以下查询数据，生成专业的业务分析报告。

            要求：
            1. 每个查询对应一个章节，章节包含：标题、2-3句分析文字、数据摘要
            2. 分析要有对比、趋势判断、异常识别
            3. 用专业但通俗的中文，管理层能直接使用
            4. 每个章节推荐一个合适的图表类型（bar/line/pie/number_card/table）
            5. chartData 格式为 {"labels":["A","B"],"values":[1,2]}，number_card 类型 values 为单值数组
            6. 严格返回纯JSON，不要markdown代码块，不要多余解释

            模板名称：{{templateName}}
            报告日期：{{reportDate}}

            查询数据：
            {{queryResults}}

            返回格式（严格遵守）：
            {"sections": [{"title": "一、总体概况", "content": "分析文字...", "chartType": "bar", "chartData": {"labels": ["A","B"], "values": [100,200]}}]}
            """;

    /**
     * 生成结构化分析。
     *
     * @param templateName 模板名称
     * @param reportDate   报告日期
     * @param queryResults ReportExecutor 产出的 {查询名: {count, rows}} 结构
     * @return 解析后的 {@link AnalysisResult}；LLM 返回非法 JSON 时降级为纯文本
     */
    public AnalysisResult generate(String templateName, String reportDate,
                                   Map<String, Object> queryResults) {
        String summary = buildSummary(queryResults);

        String prompt = ANALYSIS_PROMPT
                .replace("{{templateName}}", templateName == null ? "未命名" : templateName)
                .replace("{{reportDate}}", reportDate == null ? "" : reportDate)
                .replace("{{queryResults}}", summary);

        String response;
        try {
            response = chatModel.chat(prompt);
        } catch (Exception e) {
            log.error("AI 分析调用失败，降级为统计摘要: {}", e.getMessage());
            return fallback(templateName, queryResults, "AI 调用失败：" + e.getMessage());
        }

        try {
            AnalysisResult result = objectMapper.readValue(cleanJson(response), AnalysisResult.class);
            log.info("AI 分析生成成功，章节数={}，原文长度={}",
                    result.getSections() != null ? result.getSections().size() : 0, response.length());
            return result;
        } catch (Exception e) {
            log.warn("AI 分析JSON解析失败，降级为纯文本: {}", e.getMessage());
            return fallback(templateName, queryResults, response);
        }
    }

    /**
     * 把查询结果压缩为人类可读摘要（送给 LLM）。
     * 每个 query：名称 + 记录数 + 前 5 行样本 + 首个数值列的 min/max/avg/sum。
     */
    @SuppressWarnings("unchecked")
    private String buildSummary(Map<String, Object> queryResults) {
        if (queryResults == null || queryResults.isEmpty()) {
            return "（无查询数据）";
        }
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (Map.Entry<String, Object> entry : queryResults.entrySet()) {
            sb.append(idx++).append(". ").append(entry.getKey()).append("\n");
            Object value = entry.getValue();
            if (!(value instanceof Map)) {
                sb.append("   ").append(value).append("\n\n");
                continue;
            }
            Map<String, Object> result = (Map<String, Object>) value;
            sb.append("   记录数: ").append(result.getOrDefault("count", 0)).append("\n");
            Object rowsObj = result.get("rows");
            if (rowsObj instanceof List<?> rows && !rows.isEmpty()) {
                sb.append("   样本(前5行):\n");
                for (int i = 0; i < Math.min(rows.size(), 5); i++) {
                    sb.append("     ").append(rows.get(i)).append("\n");
                }
                if (rows.get(0) instanceof Map<?, ?> firstRow) {
                    appendNumericSummary(sb, (Map<String, Object>) firstRow, (List<Map<String, Object>>) rows);
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 对首个数值列计算 min/max/avg/sum，给 LLM 更强的量化感知。 */
    private void appendNumericSummary(StringBuilder sb, Map<String, Object> firstRow, List<Map<String, Object>> rows) {
        for (String key : firstRow.keySet()) {
            Object val = firstRow.get(key);
            if (!(val instanceof Number)) continue;
            double sum = 0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (Map<String, Object> row : rows) {
                Object v = row.get(key);
                if (!(v instanceof Number)) continue;
                double d = ((Number) v).doubleValue();
                sum += d;
                if (d < min) min = d;
                if (d > max) max = d;
            }
            sb.append(String.format("   [%s] 合计=%.2f 最小=%.2f 最大=%.2f 平均=%.2f%n",
                    key, sum, min, max, sum / rows.size()));
            break; // 只统计第一个数值列
        }
    }

    /** 降级方案：LLM 不可用或解析失败时，用统计摘要兜底。 */
    @SuppressWarnings("unchecked")
    private AnalysisResult fallback(String templateName, Map<String, Object> queryResults, String raw) {
        AnalysisResult result = new AnalysisResult();
        List<AnalysisResult.Section> sections = new ArrayList<>();
        if (queryResults != null) {
            for (Map.Entry<String, Object> entry : queryResults.entrySet()) {
                AnalysisResult.Section section = new AnalysisResult.Section();
                section.setTitle(entry.getKey());
                if (entry.getValue() instanceof Map<?, ?> m) {
                    section.setContent("记录数：" + m.get("count")
                            + (raw != null && !raw.isBlank() ? "\n\n" + raw : ""));
                } else {
                    section.setContent(String.valueOf(raw));
                }
                section.setChartType("table");
                sections.add(section);
            }
        }
        if (sections.isEmpty()) {
            AnalysisResult.Section section = new AnalysisResult.Section();
            section.setTitle(templateName == null ? "数据摘要" : templateName);
            section.setContent(raw == null || raw.isBlank() ? "无分析内容" : raw);
            section.setChartType("table");
            sections.add(section);
        }
        result.setSections(sections);
        return result;
    }

    /** 剥离 DeepSeek 常见的 markdown 代码块包裹。 */
    private String cleanJson(String response) {
        if (response == null) return "{}";
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}
