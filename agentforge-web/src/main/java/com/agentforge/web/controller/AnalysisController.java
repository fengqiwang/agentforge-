package com.agentforge.web.controller;

import com.agentforge.report.insight.AnalysisResult;
import com.agentforge.report.insight.IAnalysisService;
import com.agentforge.report.insight.TransactionAnalyzer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流水分析引擎 API。
 *
 * <ul>
 *   <li>POST /api/analysis/task — 创建多维度分析任务</li>
 *   <li>GET  /api/analysis/task/{id} — 查看分析结果</li>
 *   <li>GET  /api/analysis/task/list — 任务列表</li>
 *   <li>GET  /api/analysis/quick/{dimension} — 单维度实时分析（不入库）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final IAnalysisService analysisService;
    private final TransactionAnalyzer transactionAnalyzer;

    /** 创建多维度分析任务。body: {name, dimensions, dateRangeStart, dateRangeEnd} */
    @PostMapping("/task")
    public Map<String, Object> createTask(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> dims = (List<String>) body.getOrDefault("dimensions", List.of("time", "geo"));
        String name = (String) body.getOrDefault("name", "流水分析");
        String start = (String) body.get("dateRangeStart");
        String end = (String) body.get("dateRangeEnd");
        Long id = analysisService.createTask(name, dims, start, end);
        return Map.of("id", id, "status", "submitted", "dimensions", dims);
    }

    @GetMapping("/task/{id}")
    public Map<String, Object> getTask(@PathVariable Long id) {
        return analysisService.getById(id);
    }

    @GetMapping("/task/list")
    public List<Map<String, Object>> list() {
        return analysisService.list();
    }

    /** 快速分析：单维度实时计算（不入库）。 */
    @GetMapping("/quick/{dimension}")
    public AnalysisResult quick(@PathVariable String dimension,
                                @RequestParam(required = false) String start,
                                @RequestParam(required = false) String end) {
        return transactionAnalyzer.analyze(dimension, start, end);
    }
}
