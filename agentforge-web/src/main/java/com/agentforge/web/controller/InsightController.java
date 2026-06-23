package com.agentforge.web.controller;

import com.agentforge.report.insight.InsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 市场洞察 API：手动生成、详情、列表。
 */
@RestController
@RequestMapping("/api/insight")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    /** 手动生成洞察。body: {periodType, start, end} */
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Object> body) {
        String periodType = (String) body.getOrDefault("periodType", "MONTHLY");
        String start = (String) body.get("start");
        String end = (String) body.get("end");
        Long id = insightService.generate(periodType, start, end);
        return Map.of("id", id, "status", "generated", "periodType", periodType);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        return insightService.getById(id);
    }

    @GetMapping("/list")
    public List<Map<String, Object>> list() {
        return insightService.list();
    }
}
