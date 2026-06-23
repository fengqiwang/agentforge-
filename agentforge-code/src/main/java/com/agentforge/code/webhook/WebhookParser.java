package com.agentforge.code.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Webhook payload 解析器：把 Gitee 推送的 JSON 字符串反序列化为 {@link GiteeWebhookPayload}。
 *
 * <p>提取审查所需的核心字段：repoUrl / prNumber / commitId / 分支 / diffUrl。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookParser {

    private final ObjectMapper objectMapper;

    /** 解析原始 JSON payload。解析失败返回 null。 */
    public GiteeWebhookPayload parse(String json) {
        if (json == null || json.isBlank()) {
            log.warn("Webhook payload 为空");
            return null;
        }
        try {
            GiteeWebhookPayload payload = objectMapper.readValue(json, GiteeWebhookPayload.class);
            log.info("Webhook 解析成功: action={} pr={} repo={}",
                    payload.getAction(),
                    payload.getPullRequest() != null ? payload.getPullRequest().getNumber() : null,
                    payload.getRepository() != null ? payload.getRepository().getName() : null);
            return payload;
        } catch (Exception e) {
            log.error("Webhook payload 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** 从 payload 中提取 commit id（head sha）。 */
    public String extractCommitId(GiteeWebhookPayload payload) {
        if (payload == null || payload.getPullRequest() == null
                || payload.getPullRequest().getHead() == null) {
            return null;
        }
        return payload.getPullRequest().getHead().getSha();
    }

    /** 从 payload 中提取仓库 URL。 */
    public String extractRepoUrl(GiteeWebhookPayload payload) {
        if (payload == null || payload.getRepository() == null) {
            return null;
        }
        return payload.getRepository().getUrl();
    }
}
