package com.agentforge.web.service;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Gitee Webhook 业务编排服务接口。
 *
 * <p>职责：签名验证 → Payload 解析 → 数据提取 → 触发异步审查。
 * Controller 仅负责读取 HTTP 原始 body 和构建 HTTP 响应。
 */
public interface IGiteeWebhookService {

    /**
     * 处理 Gitee Webhook 事件。
     *
     * @param rawBody 原始请求体（用于 HMAC 签名验证）
     * @param token   X-Gitee-Token 请求头
     * @return 处理结果，Controller 据此构建 HTTP 响应
     */
    WebhookResult handleWebhook(String rawBody, String token);

    /**
     * 测试审查（免签名，直传 diff）。
     *
     * @param repoUrl   仓库地址
     * @param prNumber  PR 编号
     * @param commitId  commit sha
     * @param diff      unified diff 文本
     * @return 审查报告
     * @throws IllegalArgumentException diff 为空时抛出
     */
    Map<String, Object> testReview(String repoUrl, Integer prNumber,
                                   String commitId, String diff);

    /**
     * Webhook 处理结果 DTO，Controller 据此构建 ResponseEntity。
     */
    record WebhookResult(HttpStatus httpStatus, Map<String, Object> body) {

        public static WebhookResult forbidden(String msg) {
            return new WebhookResult(HttpStatus.FORBIDDEN,
                    Map.of("error", msg, "code", 403));
        }

        public static WebhookResult badRequest(String msg) {
            return new WebhookResult(HttpStatus.BAD_REQUEST,
                    Map.of("error", msg));
        }

        public static WebhookResult accepted(Integer prNumber) {
            return new WebhookResult(HttpStatus.ACCEPTED,
                    Map.of("status", "accepted", "prNumber", prNumber,
                           "message", "已异步排队处理"));
        }
    }
}
