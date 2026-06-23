package com.agentforge.web.service.impl;

import com.agentforge.code.webhook.GiteeWebhookPayload;
import com.agentforge.code.webhook.WebhookParser;
import com.agentforge.code.webhook.WebhookSignature;
import com.agentforge.web.service.ICodeReviewService;
import com.agentforge.web.service.IGiteeWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Gitee Webhook 业务编排服务。
 *
 * <p>将所有业务逻辑从 Controller 下沉至此：
 * <ol>
 *   <li>HMAC 签名验证</li>
 *   <li>Payload JSON 解析 + 关键字段提取</li>
 *   <li>触发异步审查流程</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GiteeWebhookServiceImpl implements IGiteeWebhookService {

    private final WebhookSignature signature;
    private final WebhookParser parser;
    private final ICodeReviewService codeReviewService;

    @Override
    public WebhookResult handleWebhook(String rawBody, String token) {
        // 1. 签名验证
        if (!signature.verify(token, rawBody)) {
            log.warn("Webhook 签名验证失败，拒绝请求");
            return WebhookResult.forbidden("签名验证失败");
        }

        // 2. 解析 payload
        GiteeWebhookPayload payload = parser.parse(rawBody);
        if (payload == null || payload.getPullRequest() == null) {
            return WebhookResult.badRequest("无效的 payload");
        }

        // 3. 提取关键数据
        GiteeWebhookPayload.PullRequest pr = payload.getPullRequest();
        String repoUrl = parser.extractRepoUrl(payload);
        String commitId = parser.extractCommitId(payload);
        Integer prNumber = pr.getNumber();
        String patchUrl = pr.getPatchUrl();

        log.info("收到 Gitee Webhook: action={} repo={} pr={}", payload.getAction(), repoUrl, prNumber);

        // 4. 异步处理：拉取 diff → 审查 → 评论（避免 Gitee 10s 超时）
        codeReviewService.reviewAsyncWithFetch(repoUrl, prNumber, commitId, patchUrl);

        return WebhookResult.accepted(prNumber);
    }

    @Override
    public Map<String, Object> testReview(String repoUrl, Integer prNumber,
                                          String commitId, String diff) {
        if (diff == null || diff.isBlank()) {
            throw new IllegalArgumentException("缺少 diff");
        }
        return codeReviewService.reviewAndStore(repoUrl, prNumber, commitId, diff);
    }
}
