package com.agentforge.web.controller;

import com.agentforge.report.business.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/report-template")
@RequiredArgsConstructor
public class BusinessReportController {

    private final IReportTemplateService templateService;
    private final ReportExecutor reportExecutor;
    private final IBusinessReportService reportService;
    private final ReportAssembler reportAssembler;
    private final ReportExporter reportExporter;
    private final IReportComparisonService comparisonService;

    // ===== 模板 CRUD =====
    @PostMapping
    public ReportTemplate create(@RequestBody ReportTemplate template) {
        return templateService.create(template);
    }

    @PutMapping("/{id}")
    public ReportTemplate update(@PathVariable Long id, @RequestBody ReportTemplate template) {
        return templateService.update(id, template);
    }

    @GetMapping
    public List<ReportTemplate> list() {
        return templateService.list();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        templateService.delete(id);
    }

    // ===== 报告执行 =====
    @PostMapping("/{id}/execute")
    public BusinessReport execute(@PathVariable Long id) {
        return reportExecutor.execute(id);
    }

    // ===== 报告历史 =====
    @GetMapping("/reports")
    public List<BusinessReport> history(@RequestParam(defaultValue = "20") int limit) {
        return reportService.listRecent(limit);
    }

    @GetMapping("/reports/{id}")
    public BusinessReport getReport(@PathVariable Long id) {
        return reportService.getById(id);
    }

    @GetMapping("/reports/{id}/full")
    public Map<String, Object> getFullReport(@PathVariable Long id) {
        BusinessReport report = reportService.getById(id);
        if (report == null) {
            return Map.of("error", "报告不存在: " + id);
        }
        if ((report.getAiAnalysis() == null || report.getAiAnalysis().isBlank())
                && ReportStatus.COMPLETED.getCode().equals(report.getStatus())) {
            report = reportAssembler.assemble(report);
        }
        return reportAssembler.toFullReportMap(report);
    }

    @GetMapping("/reports/{id}/export/html")
    public ResponseEntity<String> exportHtml(@PathVariable Long id) {
        String html = reportExporter.toHtml(id);
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(html);
    }

    @GetMapping("/reports/compare")
    public Map<String, Object> compare(@RequestParam Long id1, @RequestParam Long id2) {
        return comparisonService.compare(id1, id2);
    }
}
