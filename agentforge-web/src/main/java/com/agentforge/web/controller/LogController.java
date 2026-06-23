package com.agentforge.web.controller;

import com.agentforge.web.service.ILogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 日志查询 API。
 */
@RestController
@RequestMapping("/api/log")
@RequiredArgsConstructor
public class LogController {

    private final ILogQueryService logQueryService;

    @GetMapping("/sql")
    public Map<String, Object> sqlLogs(
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return logQueryService.sqlLogs(sessionId, page, size);
    }

    @GetMapping("/agent")
    public Map<String, Object> agentLogs(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String agentName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return logQueryService.agentLogs(sessionId, agentName, page, size);
    }

    @GetMapping("/sql/stats")
    public Map<String, Object> sqlStats() {
        return logQueryService.sqlStats();
    }
}
