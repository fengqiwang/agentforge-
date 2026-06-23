package com.agentforge.report.business;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * AI 分析结构化结果。
 *
 * <p>由 {@link AiAnalysisGenerator} 调用 LLM 产出，每个查询对应一个 {@link Section}：
 * <ul>
 *   <li>title   — 章节标题（如「一、总体概况」）</li>
 *   <li>content — 2~3 句中文分析文字</li>
 *   <li>chartType — 推荐图表类型：bar / line / pie / number_card / table</li>
 *   <li>chartData — 图表数据：{labels:[...], values:[...]}</li>
 * </ul>
 */
@Data
public class AnalysisResult {

    private List<Section> sections;

    @Data
    public static class Section {
        /** 章节标题 */
        private String title;
        /** AI 生成的分析文字 */
        private String content;
        /** 图表类型：bar / line / pie / number_card / table */
        private String chartType;
        /** 图表数据 */
        private Map<String, Object> chartData;
    }
}
