package com.agentforge.web.controller;

import com.agentforge.common.model.reconciliation.ReconciliationResult;
import com.agentforge.report.reconciliation.ReconciliationExporter;
import com.agentforge.report.reconciliation.ReconciliationRuleConfig;
import com.agentforge.report.reconciliation.ReconciliationService;
import com.agentforge.report.reconciliation.ReconciliationSummaryGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/reconciliation")
@RequiredArgsConstructor
public class ReconciliationExportController {

    private final ReconciliationService reconciliationService;
    private final ReconciliationExporter exporter;
    private final ReconciliationSummaryGenerator summaryGenerator;
    private final ReconciliationRuleConfig ruleConfig;

    @GetMapping("/{id}/summary")
    public ResponseEntity<?> getSummary(@PathVariable Long id) {
        Map<String, Object> batch = reconciliationService.getBatch(id);
        if (batch.containsKey("error")) {
            return ResponseEntity.status(404).body(batch);
        }
        ReconciliationResult result = reconciliationService.getResult(id);
        if (result == null) {
            return ResponseEntity.status(404).body(Map.of("error", "对账结果不存在"));
        }
        String summary = summaryGenerator.generate(result);
        return ResponseEntity.ok(Map.of("summary", summary, "batch", batch));
    }

    @GetMapping("/{id}/export/excel")
    public ResponseEntity<byte[]> exportExcel(@PathVariable Long id) {
        Map<String, Object> batch = reconciliationService.getBatch(id);
        if (batch.containsKey("error")) return ResponseEntity.notFound().build();
        ReconciliationResult result = reconciliationService.getResult(id);
        if (result == null) return ResponseEntity.notFound().build();
        String batchName = (String) batch.getOrDefault("name", "reconciliation");
        try {
            String summary = summaryGenerator.generate(result);
            byte[] excelBytes = exporter.exportExcel(result, batchName, summary);
            String filename = URLEncoder.encode(batchName + "_对账报告.xlsx", StandardCharsets.UTF_8)
                    .replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelBytes);
        } catch (Exception e) {
            log.error("Excel导出失败：batchId={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // =============== 规则配置 API ===============

    @GetMapping("/rules")
    public ResponseEntity<ReconciliationRuleConfig.Rules> getRules() {
        return ResponseEntity.ok(ruleConfig.getRules());
    }

    @PutMapping("/rules")
    public ResponseEntity<ReconciliationRuleConfig.Rules> updateRules(
            @RequestBody ReconciliationRuleConfig.Rules newRules) {
        ruleConfig.updateRules(newRules);
        return ResponseEntity.ok(ruleConfig.getRules());
    }
}
