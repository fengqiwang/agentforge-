package com.agentforge.web.service.impl;

import com.agentforge.code.diff.DiffFile;
import com.agentforge.code.diff.DiffParser;
import com.agentforge.code.gitee.CommentBuilder;
import com.agentforge.code.gitee.GiteeApiClient;
import com.agentforge.code.review.CodeReviewer;
import com.agentforge.code.review.ReviewResult;
import com.agentforge.code.review.Severity;
import com.agentforge.web.service.ICodeReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码审查编排服务。
 *
 * <p>流程：DiffParser 解析多文件 → {@link CodeReviewer} 逐文件 LLM 审查 →
 * 聚合统计（CRITICAL/WARNING/SUGGESTION 计数 + verdict）→ 存入 af_code_review。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeReviewServiceImpl implements ICodeReviewService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(15);

    private final DiffParser diffParser;
    private final CodeReviewer codeReviewer;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CommentBuilder commentBuilder;
    private final GiteeApiClient giteeApiClient;

    /** owner/repo，配置项 agentforge.gitee.owner-repo */
    @Value("${agentforge.gitee.owner-repo:}")
    private String ownerRepo;

    /**
     * 执行完整审查并持久化。
     *
     * @param repoUrl  仓库地址
     * @param prNumber PR 编号
     * @param commitId commit sha
     * @param diff     unified diff 文本
     * @return 聚合后的审查报告（同时已写入 af_code_review）
     */
    @Override
    public Map<String, Object> reviewAndStore(String repoUrl, Integer prNumber,
                                              String commitId, String diff) {
        long start = System.currentTimeMillis();

        // 1. 解析 diff
        List<DiffFile> files = diffParser.parse(diff);
        log.info("代码审查开始 repo={} pr={} 文件数={}", repoUrl, prNumber, files.size());

        // 2. 逐文件审查
        List<ReviewResult> results = files.stream()
                .filter(f -> f.changedLineCount() > 0)   // 跳过纯删除/空文件
                .map(codeReviewer::review)
                .toList();

        // 3. 聚合
        Map<String, Object> report = aggregate(repoUrl, prNumber, commitId, results);

        // 4. 持久化
        Long reviewId = persist(repoUrl, prNumber, commitId, report);
        report.put("reviewId", reviewId);
        report.put("elapsedMs", System.currentTimeMillis() - start);
        return report;
    }

    /**
     * 审查 + 存储 + 发布评论（同步）。
     * 始终构建 Markdown 评论放入响应；仅当 postComment=true 且 Gitee token 已配置时实际发布。
     *
     * @param repoUrl    仓库地址（用于存储）
     * @param prNumber   PR 编号（用于评论定位，可空）
     * @param commitId   commit sha
     * @param diff       unified diff
     * @param postComment 是否尝试发布到 Gitee
     */
    @Override
    public Map<String, Object> reviewStoreAndComment(String repoUrl, Integer prNumber,
                                                     String commitId, String diff, boolean postComment) {
        Map<String, Object> report = reviewAndStore(repoUrl, prNumber, commitId, diff);
        Long reviewId = (Long) report.get("reviewId");

        // 构建 Markdown 评论（始终放入响应，便于预览）
        List<ReviewResult> results = (List<ReviewResult>) report.get("fileResults");
        String markdown = commentBuilder.buildPrComment(results);
        report.put("markdownComment", markdown);

        if (postComment && prNumber != null && reviewId != null) {
            boolean ok = giteeApiClient.postPrComment(ownerRepo, prNumber, markdown);
            if (ok) {
                markCommentPosted(reviewId);
            }
            report.put("commentPosted", ok);
        } else {
            report.put("commentPosted", false);
        }
        return report;
    }

    /** 标记评论已发布。 */
    private void markCommentPosted(Long reviewId) {
        jdbcTemplate.update("UPDATE af_code_review SET comment_posted=1 WHERE id=?", reviewId);
    }

    /** 查询单条审查记录详情。 */
    @Override
    public Map<String, Object> getById(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT id, repo_url, pr_number, commit_id, review_result, comment_posted, created_at " +
                        "FROM af_code_review WHERE id=?",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("repoUrl", rs.getString("repo_url"));
                    m.put("prNumber", rs.getInt("pr_number"));
                    m.put("commitId", rs.getString("commit_id"));
                    m.put("commentPosted", rs.getBoolean("comment_posted"));
                    m.put("createdAt", rs.getTimestamp("created_at"));
                    Object parsed;
                    try {
                        parsed = objectMapper.readValue(rs.getString("review_result"), Object.class);
                    } catch (Exception e) {
                        parsed = rs.getString("review_result");
                    }
                    m.put("reviewResult", parsed);
                    return m;
                }, id);
    }

    /** Webhook 异步入口：拉取 diff → 审查 → 存储 → 发评论。 */
    @Override
    @org.springframework.scheduling.annotation.Async
    public void reviewAsync(String repoUrl, Integer prNumber, String commitId, String diff) {
        try {
            reviewStoreAndComment(repoUrl, prNumber, commitId, diff, true);
        } catch (Exception e) {
            log.error("异步审查失败 pr={}", prNumber, e);
        }
    }

    /** Webhook 异步入口（含 diff 拉取）：先 fetch patch_url，再审查评论。 */
    @Override
    @org.springframework.scheduling.annotation.Async
    public void reviewAsyncWithFetch(String repoUrl, Integer prNumber, String commitId, String patchUrl) {
        try {
            String diff = fetchDiff(patchUrl);
            if (diff == null || diff.isBlank()) {
                log.warn("异步审查：diff 为空，跳过 pr={}", prNumber);
                return;
            }
            reviewStoreAndComment(repoUrl, prNumber, commitId, diff, true);
        } catch (Exception e) {
            log.error("异步审查失败 pr={} url={}", prNumber, patchUrl, e);
        }
    }

    /** 拉取 diff 文本。 */
    private String fetchDiff(String url) throws Exception {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("patch_url 为空");
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(FETCH_TIMEOUT).GET().build();
        HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + resp.statusCode());
        }
        return resp.body();
    }



    /** 聚合各文件审查结果为整 PR 报告。 */
    private Map<String, Object> aggregate(String repoUrl, Integer prNumber,
                                          String commitId, List<ReviewResult> results) {
        long critical = 0, warning = 0, suggestion = 0;
        long filesBlocked = 0;
        for (ReviewResult r : results) {
            for (var issue : r.getIssues()) {
                if (issue.getSeverity() == Severity.CRITICAL) critical++;
                else if (issue.getSeverity() == Severity.WARNING) warning++;
                else suggestion++;
            }
            if ("BLOCK".equals(r.getVerdict())) filesBlocked++;
        }

        String prVerdict;
        if (critical > 0 || filesBlocked > 0) prVerdict = "BLOCK";
        else if (warning > 0) prVerdict = "NEED_FIX";
        else prVerdict = "PASS";

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("repoUrl", repoUrl);
        report.put("prNumber", prNumber);
        report.put("commitId", commitId);
        report.put("verdict", prVerdict);
        report.put("stats", Map.of(
                "files", results.size(),
                "critical", critical,
                "warning", warning,
                "suggestion", suggestion,
                "blockedFiles", filesBlocked
        ));
        report.put("summary", String.format(
                "审查 %d 个文件：严重 %d、警告 %d、建议 %d，结论 %s",
                results.size(), critical, warning, suggestion, prVerdict));
        report.put("fileResults", results);
        return report;
    }

    /** 写入 af_code_review 表。 */
    private Long persist(String repoUrl, Integer prNumber, String commitId, Map<String, Object> report) {
        try {
            String json = objectMapper.writeValueAsString(report);
            jdbcTemplate.update(
                    "INSERT INTO af_code_review (repo_url, pr_number, commit_id, review_result, comment_posted) VALUES (?,?,?,?,?)",
                    repoUrl, prNumber, commitId, json, false);
            Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            log.info("审查结果已存储 reviewId={} pr={}", id, prNumber);
            return id;
        } catch (Exception e) {
            log.error("审查结果存储失败", e);
            return null;
        }
    }

    /** 查询历史审查记录。 */
    @Override
    public List<Map<String, Object>> listRecent(int limit) {
        return jdbcTemplate.query(
                "SELECT id, repo_url, pr_number, commit_id, review_result, comment_posted, created_at " +
                        "FROM af_code_review ORDER BY id DESC LIMIT ?",
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("repoUrl", rs.getString("repo_url"));
                    m.put("prNumber", rs.getInt("pr_number"));
                    m.put("commitId", rs.getString("commit_id"));
                    m.put("commentPosted", rs.getBoolean("comment_posted"));
                    m.put("createdAt", rs.getTimestamp("created_at"));
                    Object parsed = null;
                    try {
                        parsed = objectMapper.readValue(rs.getString("review_result"), Object.class);
                    } catch (Exception e) {
                        log.debug("review_result JSON 解析失败，返回原始字符串: {}", e.getMessage());
                        parsed = rs.getString("review_result");
                    }
                    m.put("reviewResult", parsed);
                    return m;
                }, limit);
    }
}
