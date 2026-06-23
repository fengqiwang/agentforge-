package com.agentforge.code.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/**
 * Gitee Pull Request Webhook payload（精简版，只保留审查所需字段）。
 *
 * <p>Gitee merge_request 事件 payload 结构（节选）：
 * <pre>
 * {
 *   "action": "open",
 *   "pull_request": {
 *     "number": 12,
 *     "title": "feat: add login",
 *     "head": { "ref": "feature/login", "sha": "abc123..." },
 *     "base": { "ref": "master" },
 *     "patch_url": "https://gitee.com/.../pulls/12.diff"
 *   },
 *   "repository": {
 *     "name": "demo",
 *     "url": "https://gitee.com/owner/demo"
 *   }
 * }
 * </pre>
 *
 * <p>用 {@link JsonIgnoreProperties}(ignoreUnknown=true) 容忍 payload 中未映射的多余字段。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GiteeWebhookPayload {

    /** 动作：open / update / merge / close */
    private String action;

    private PullRequest pullRequest;

    private Repository repository;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PullRequest {
        private Integer number;
        private String title;
        private Ref head;
        private Ref base;
        /** diff 文件下载地址 */
        private String patchUrl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Ref {
        private String ref;
        private String sha;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {
        private String name;
        private String url;
    }
}
