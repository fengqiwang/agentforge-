package com.agentforge.report.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 报告导出器：把 {@link BusinessReport} 渲染为自包含 HTML，浏览器可直接打印为 PDF。
 *
 * <p>HTML 包含：
 * <ul>
 *   <li>报告标题 + 报告日期 + 生成时间</li>
 *   <li>AI 分析章节（标题 + 分析文字）</li>
 *   <li>附录：每个查询的原始数据表格（前 50 行）</li>
 * </ul>
 *
 * <p>采用纯 HTML+CSS，无需额外 PDF 依赖，规避中文字体渲染问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportExporter {

    private final BusinessReportService reportService;
    private final ObjectMapper objectMapper;

    /** 渲染报告为完整 HTML 文档。 */
    public String toHtml(Long reportId) {
        BusinessReport report = reportService.getById(reportId);
        if (report == null) {
            return "<html><body><h1>报告不存在</h1></body></html>";
        }

        StringBuilder html = new StringBuilder(4096);
        html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        html.append("<title>").append(safe(report.getTemplateName())).append("</title>");
        html.append("<style>");
        html.append("* { box-sizing: border-box; }");
        html.append("body { font-family: 'Microsoft YaHei','PingFang SC',sans-serif; max-width: 900px; margin: 0 auto; padding: 40px; color: #333; }");
        html.append("h1 { text-align: center; font-size: 24px; border-bottom: 2px solid #409EFF; padding-bottom: 12px; }");
        html.append("h2 { font-size: 17px; border-left: 4px solid #409EFF; padding-left: 10px; margin-top: 28px; }");
        html.append("h3 { font-size: 14px; color: #666; }");
        html.append(".meta { text-align: center; color: #999; font-size: 13px; margin: 8px 0 24px; }");
        html.append(".status { display: inline-block; padding: 2px 10px; border-radius: 10px; font-size: 12px; }");
        html.append(".status.COMPLETED { background: #e1f3d8; color: #67c23a; }");
        html.append(".status.FAILED { background: #fde2e2; color: #f56c6c; }");
        html.append(".status.GENERATING { background: #faecd8; color: #e6a23c; }");
        html.append(".content { color: #555; line-height: 1.8; text-indent: 2em; font-size: 14px; }");
        html.append(".chart-type { color: #409EFF; font-size: 12px; margin-left: 8px; }");
        html.append("table { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 13px; }");
        html.append("th, td { border: 1px solid #ddd; padding: 6px 8px; text-align: left; }");
        html.append("th { background: #f5f7fa; font-weight: 600; }");
        html.append("tr:nth-child(even) { background: #fafafa; }");
        html.append("footer { margin-top: 40px; padding-top: 16px; border-top: 1px solid #eee; color: #bbb; font-size: 12px; text-align: center; }");
        html.append("@media print { body { padding: 0; max-width: none; } }");
        html.append("</style></head><body>");

        // 标题区
        html.append("<h1>").append(safe(report.getTemplateName())).append("</h1>");
        html.append("<div class='meta'>报告日期：").append(safe(report.getReportDate()))
                .append(" &nbsp;|&nbsp; 生成时间：").append(report.getCreatedAt() == null ? "-" : report.getCreatedAt())
                .append(" &nbsp;|&nbsp; 状态：<span class='status ").append(safe(report.getStatus())).append("'>")
                .append(safe(report.getStatus())).append("</span></div>");

        // AI 分析章节
        renderAiAnalysis(html, report.getAiAnalysis());

        // 原始数据附录
        renderDataAppendix(html, report.getDataResult());

        html.append("<footer>本报告由 AgentForge 智能报表对话引擎自动生成 · 数据来源：业务系统实时查询</footer>");
        html.append("</body></html>");
        return html.toString();
    }

    @SuppressWarnings("unchecked")
    private void renderAiAnalysis(StringBuilder html, String aiAnalysis) {
        if (aiAnalysis == null || aiAnalysis.isBlank()) return;
        try {
            AnalysisResult analysis = objectMapper.readValue(aiAnalysis, AnalysisResult.class);
            if (analysis.getSections() == null || analysis.getSections().isEmpty()) return;

            for (AnalysisResult.Section s : analysis.getSections()) {
                html.append("<h2>").append(safe(s.getTitle()));
                if (s.getChartType() != null && !s.getChartType().isBlank()) {
                    html.append("<span class='chart-type'>[").append(safe(s.getChartType())).append("]</span>");
                }
                html.append("</h2>");
                html.append("<p class='content'>").append(safe(s.getContent())).append("</p>");
            }
        } catch (Exception e) {
            log.warn("ai_analysis 解析失败，按纯文本渲染: {}", e.getMessage());
            html.append("<h2>AI 分析</h2><pre>").append(safe(aiAnalysis)).append("</pre>");
        }
    }

    @SuppressWarnings("unchecked")
    private void renderDataAppendix(StringBuilder html, Map<String, Object> dataResult) {
        if (dataResult == null || dataResult.isEmpty()) return;
        html.append("<h2 style='margin-top:36px'>附录：原始数据</h2>");
        for (Map.Entry<String, Object> entry : dataResult.entrySet()) {
            html.append("<h3>").append(safe(entry.getKey())).append("</h3>");
            if (!(entry.getValue() instanceof Map<?, ?> result)) continue;
            Object rowsObj = result.get("rows");
            if (!(rowsObj instanceof List<?> rows) || rows.isEmpty()) {
                html.append("<p style='color:#999'>（无数据）</p>");
                continue;
            }
            html.append("<table>");
            // 表头
            if (rows.get(0) instanceof Map<?, ?> firstRow) {
                html.append("<tr>");
                for (Object col : firstRow.keySet()) {
                    html.append("<th>").append(safe(String.valueOf(col))).append("</th>");
                }
                html.append("</tr>");
                // 数据行（最多 50 行）
                int limit = Math.min(rows.size(), 50);
                for (int i = 0; i < limit; i++) {
                    Map<?, ?> row = (Map<?, ?>) rows.get(i);
                    html.append("<tr>");
                    for (Object val : row.values()) {
                        html.append("<td>").append(val == null ? "" : safe(String.valueOf(val))).append("</td>");
                    }
                    html.append("</tr>");
                }
            }
            html.append("</table>");
            if (rows.size() > 50) {
                html.append("<p style='color:#999;font-size:12px'>（仅显示前 50 行，共 ")
                        .append(rows.size()).append(" 行）</p>");
            }
        }
    }

    /** HTML 转义，防注入。 */
    private String safe(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
