package com.agentforge.report.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报告组装器：把 ReportExecutor 产出的查询结果 + AI 分析组装为完整报告，并落库。
 *
 * <p>调用时机：{@link ReportExecutor} 成功执行完所有 SQL（status=COMPLETED）后调用
 * {@link #assemble}，生成 AI 分析并写回 {@code af_business_report.ai_analysis}。
 *
 * <p>ai_analysis 字段存储 {@link AnalysisResult} 序列化后的 JSON 字符串，
 * 前端 {@code /reports/{id}/full} 接口反序列化后渲染。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportAssembler {

    private final AiAnalysisGenerator aiGenerator;
    private final IBusinessReportService reportService;
    private final ObjectMapper objectMapper;

    private static final String EMPTY_ANALYSIS_JSON = "{\"sections\":[]}";

    /**
     * 组装完整报告：数据 + AI 分析 → 更新到数据库。
     *
     * @param report 已执行完 SQL 的报告（status 应为 COMPLETED）
     * @return 更新后的报告
     */
    public BusinessReport assemble(BusinessReport report) {
        if (report == null) return null;
        if (!ReportStatus.COMPLETED.getCode().equals(report.getStatus())) {
            log.info("报告非 COMPLETED 状态({})，跳过AI组装: id={}", report.getStatus(), report.getId());
            return report;
        }

        Map<String, Object> dataResult = report.getDataResult();
        if (dataResult == null || dataResult.isEmpty()) {
            report.setAiAnalysis(EMPTY_ANALYSIS_JSON);
            reportService.update(report);
            return report;
        }

        log.info("开始组装报告AI分析: id={} template={} queryCount={}",
                report.getId(), report.getTemplateName(), dataResult.size());

        AnalysisResult analysis = aiGenerator.generate(
                report.getTemplateName(),
                report.getReportDate(),
                dataResult
        );

        try {
            report.setAiAnalysis(objectMapper.writeValueAsString(analysis));
        } catch (Exception e) {
            log.error("AnalysisResult 序列化失败，降级存储空结构", e);
            report.setAiAnalysis(EMPTY_ANALYSIS_JSON);
        }

        reportService.update(report);
        log.info("报告AI分析组装完成: id={} sections={}",
                report.getId(),
                analysis.getSections() != null ? analysis.getSections().size() : 0);
        return report;
    }

    /** 将 BusinessReport 转为前端 /full 接口所需的 Map 结构。 */
    public Map<String, Object> toFullReportMap(BusinessReport report) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", report.getId());
        result.put("templateName", report.getTemplateName());
        result.put("reportDate", report.getReportDate());
        result.put("status", report.getStatus());
        result.put("dataResult", report.getDataResult() != null ? report.getDataResult() : Map.of());
        result.put("aiAnalysis", parseAiAnalysis(report.getAiAnalysis()));
        result.put("createdAt", report.getCreatedAt() == null ? null : report.getCreatedAt().toString());
        return result;
    }

    private Object parseAiAnalysis(String aiAnalysis) {
        if (aiAnalysis == null || aiAnalysis.isBlank()) return null;
        try {
            return objectMapper.readValue(aiAnalysis, Object.class);
        } catch (Exception e) {
            return Map.of("raw", aiAnalysis);
        }
    }
}
