package com.agentforge.web.controller;

import com.agentforge.web.service.ICodeReviewService;
import com.agentforge.web.service.IGiteeWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gitee Webhook 接收控制器。
 *
 * <p>端点：
 * <ul>
 *   <li>{@code POST /api/webhook/gitee} — Gitee 推送的 merge_request 事件（HMAC 签名验证）</li>
 *   <li>{@code POST /api/webhook/gitee/test} — 直传 diff 跑审查（本地测试用，免签名）</li>
 *   <li>{@code GET  /api/webhook/reviews} — 查询审查历史</li>
 * </ul>
 *
 * <p>Controller 仅负责 HTTP 层面：读取原始 body + 调用 Service + 返回 HTTP 响应。
 * 签名验证、Payload 解析、数据提取、审查触发等业务逻辑全部下沉至 {@link IGiteeWebhookService}。
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class GiteeWebhookController {

    private final IGiteeWebhookService webhookService;
    private final ICodeReviewService codeReviewService;

    private static final String TOKEN_HEADER = "X-Gitee-Token";

    /**
     * Gitee merge_request 事件入口。
     */
    @PostMapping(value = "/gitee", consumes = "application/json")
    public ResponseEntity<?> handleGitee(HttpServletRequest request,
                                         @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        String rawBody = readRawBody(request);
        if (rawBody == null) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "读取请求体失败"));
        }

        IGiteeWebhookService.WebhookResult result = webhookService.handleWebhook(rawBody, token);
        return ResponseEntity.status(result.httpStatus()).body(result.body());
    }

    /**
     * 测试端点：直传 diff 跑审查（免签名验证），用于本地调试。
     */
    @PostMapping("/gitee/test")
    public ResponseEntity<?> testReview(@RequestBody Map<String, Object> body) {
        String repoUrl = (String) body.getOrDefault("repoUrl", "local/test-repo");
        Integer prNumber = body.get("prNumber") instanceof Number n
                ? n.intValue() : 1;
        String commitId = (String) body.get("commitId");
        String diff = (String) body.get("diff");

        try {
            return ResponseEntity.ok(
                    webhookService.testReview(repoUrl, prNumber, commitId, diff));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 审查历史。 */
    @GetMapping("/reviews")
    public List<Map<String, Object>> reviews(@RequestParam(defaultValue = "20") int limit) {
        return codeReviewService.listRecent(limit);
    }

    /**
     * 读取原始请求体，用于 HMAC 签名验证。
     *
     * @return 请求体原文；IO 异常时返回 null（由调用方决定如何处理）
     */
    private String readRawBody(HttpServletRequest request) {
        try (BufferedReader reader = request.getReader()) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            log.error("读取 Webhook body 失败", e);
            return null;
        }
    }
}
