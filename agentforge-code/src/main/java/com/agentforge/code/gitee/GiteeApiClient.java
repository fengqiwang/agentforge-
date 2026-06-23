package com.agentforge.code.gitee;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gitee REST API v5 客户端：在 PR 下发表评论。
 *
 * <p>PR 评论：POST https://gitee.com/api/v5/repos/{owner}/{repo}/pulls/{number}/comments
 * <p>token 从配置项 agentforge.gitee.token 读取；未配置则跳过（仅本地记录）。
 */
@Slf4j
@Component
public class GiteeApiClient {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String token;

    public GiteeApiClient(@Value("${agentforge.gitee.token:}") String token) {
        this.token = token;
    }

    /** 是否配置了可用 token。 */
    public boolean isConfigured() {
        return token != null && !token.isBlank();
    }

    /** 发表整 PR 评论。返回是否成功。 */
    public boolean postPrComment(String ownerRepo, int prNumber, String body) {
        if (!isConfigured()) {
            log.warn("Gitee token 未配置，跳过评论发布（仅记录到本地）");
            return false;
        }
        String url = String.format("https://gitee.com/api/v5/repos/%s/pulls/%d/comments", ownerRepo, prNumber);
        return post(url, Map.of("access_token", token, "body", body));
    }

    /** 发表行级评论。 */
    public boolean postLineComment(String ownerRepo, int prNumber, String file, int line, String body) {
        if (!isConfigured()) return false;
        String url = String.format("https://gitee.com/api/v5/repos/%s/pulls/%d/comments", ownerRepo, prNumber);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("access_token", token);
        payload.put("body", body);
        payload.put("filepath", file);
        payload.put("line_number", line);
        return post(url, payload);
    }

    private boolean post(String url, Map<String, Object> payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 300) {
                log.info("Gitee 评论发布成功 url={} status={}", url, resp.statusCode());
                return true;
            }
            log.warn("Gitee 评论发布失败 status={} body={}", resp.statusCode(), resp.body());
            return false;
        } catch (Exception e) {
            log.error("Gitee API 调用异常: {}", e.getMessage());
            return false;
        }
    }
}
