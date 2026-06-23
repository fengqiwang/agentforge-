package com.agentforge.web.controller;

import com.agentforge.code.webhook.GiteeWebhookPayload;
import com.agentforge.code.webhook.WebhookParser;
import com.agentforge.code.webhook.WebhookSignature;
import com.agentforge.web.service.CodeReviewService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gitee Webhook 接收控制器。
 *
 * <p>端点：
 * <ul>
 *   <li>{@code POST /api/webhook/gitee} — Gitee 实际推送的 merge_request 事件（HMAC 签名验证）</li>
 *   <li>{@code POST /api/webhook/gitee/test} — 直传 diff 跑审查（本地测试用，免签名）</li>
 *   <li>{@code GET  /api/webhook/reviews} — 查询审查历史</li>
 * </ul>
 *
 * <p>签名验证失败返回 403，对应验收 P3-02。
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class GiteeWebhookController {

    private final WebhookSignature signature;
    private final WebhookParser parser;
    private final CodeReviewService codeReviewService;

    private static final String TOKEN_HEADER = "X-Gitee-Token";

    /**
     * Gitee merge_request 事件入口。
     * 读取原始 body（HMAC 需原始字节），验证签名后解析并触发审查。
     */
    @PostMapping(value = "/gitee", consumes = "application/json")
    public ResponseEntity<?> handleGitee(HttpServletRequest request,
                                         @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        String rawBody = readRawBody(request);

        // 1. 签名验证
        if (!signature.verify(token, rawBody)) {
            log.warn("Webhook 签名验证失败，拒绝请求");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "签名验证失败", "code", 403));
        }

        // 2. 解析 payload
        GiteeWebhookPayload payload = parser.parse(rawBody);
        if (payload == null || payload.getPullRequest() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的 payload"));
        }

        GiteeWebhookPayload.PullRequest pr = payload.getPullRequest();
        String repoUrl = parser.extractRepoUrl(payload);
        String commitId = parser.extractCommitId(payload);
        Integer prNumber = pr.getNumber();

        log.info("收到 Gitee Webhook: action={} repo={} pr={}", payload.getAction(), repoUrl, prNumber);

        // 3. 异步处理：拉取 diff + 审查 + 评论，立即返回（避免 Gitee 10s 超时）
        String patchUrl = pr.getPatchUrl();
        codeReviewService.reviewAsyncWithFetch(repoUrl, prNumber, commitId, patchUrl);

        return ResponseEntity.accepted()
                .body(Map.of("status", "accepted", "prNumber", prNumber, "message", "已异步排队处理"));
    }

    /**
     * 测试端点：直接传入 {repoUrl, prNumber, commitId, diff}，跳过签名，
     * 用于本地验证 diff 解析 + review + 存储全链路。
     */
    @PostMapping("/gitee/test")
    public ResponseEntity<?> testReview(@RequestBody Map<String, Object> body) {
        String repoUrl = (String) body.getOrDefault("repoUrl", "local/test-repo");
        Integer prNumber = toInt(body.get("prNumber"), 1);
        String commitId = (String) body.get("commitId");
        String diff = (String) body.get("diff");
        if (diff == null || diff.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少 diff"));
        }
        Map<String, Object> report = codeReviewService.reviewAndStore(repoUrl, prNumber, commitId, diff);
        return ResponseEntity.ok(report);
    }

    /** 审查历史。 */
    @GetMapping("/reviews")
    public List<Map<String, Object>> reviews(@RequestParam(defaultValue = "20") int limit) {
        return codeReviewService.listRecent(limit);
    }

    /** 读取原始请求体（保留字节用于 HMAC）。 */
    private String readRawBody(HttpServletRequest request) {
        try (BufferedReader reader = request.getReader()) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("读取 body 失败", e);
            return "";
        }
    }

    private Integer toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
        }
        return def;
    }
}
