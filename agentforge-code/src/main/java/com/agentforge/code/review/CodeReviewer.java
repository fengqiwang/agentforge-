package com.agentforge.code.review;

import com.agentforge.code.diff.DiffFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 代码审查器：对单个 {@link DiffFile} 调用 LLM 生成结构化 {@link ReviewResult}。
 *
 * <p>覆盖 6 个审查维度：
 * <ol>
 *   <li>命名规范</li>
 *   <li>空指针风险</li>
 *   <li>资源泄漏</li>
 *   <li>SQL注入风险</li>
 *   <li>N+1查询</li>
 *   <li>硬编码密钥</li>
 * </ol>
 *
 * <p>Severity 分级：CRITICAL（SQL注入/硬编码密钥等安全漏洞）/ WARNING（空指针/资源泄漏等潜在Bug）/
 * SUGGESTION（命名/风格建议）。
 *
 * <p>要求 LLM 返回 JSON，解析失败时降级为关键词规则扫描，保证始终有结构化产出。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeReviewer {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    private static final String REVIEW_PROMPT = """
            你是资深代码审查专家。审查以下代码变更，严格按 6 个维度检查：
            1. 命名规范 — 变量名/方法名是否符合规范
            2. 空指针风险 — 未判空的引用
            3. 资源泄漏 — 未关闭的IO流/数据库连接
            4. SQL注入风险 — 字符串拼接SQL
            5. N+1查询 — 循环中执行数据库查询
            6. 硬编码密钥 — 代码中包含API Key/密码/token

            严重度分级：
            - CRITICAL：安全漏洞（SQL注入、硬编码密钥）
            - WARNING：潜在Bug（空指针、资源泄漏、N+1查询）
            - SUGGESTION：规范建议（命名、风格）

            文件：{{file}}
            分类：{{category}}
            变更类型：{{changeType}}

            代码变更：
            {{diff}}

            只返回纯JSON，格式如下（无问题时 issues 返回空数组）：
            {"verdict": "PASS|NEED_FIX|BLOCK", "summary": "一句话总结", "issues": [{"dimension": "维度", "severity": "CRITICAL|WARNING|SUGGESTION", "line": 12, "message": "问题描述", "suggestion": "修复建议"}]}
            """;

    /** 审查单个文件。 */
    public ReviewResult review(DiffFile file) {
        ReviewResult result = ReviewResult.of(file);

        String diffText = formatDiffForLlm(file);
        String prompt = REVIEW_PROMPT
                .replace("{{file}}", safe(file.getPath()))
                .replace("{{category}}", safe(file.getCategory()))
                .replace("{{changeType}}", safe(file.getChangeType()))
                .replace("{{diff}}", diffText);

        String response;
        try {
            response = chatModel.chat(prompt);
        } catch (Exception e) {
            log.warn("Review LLM 调用失败，降级规则扫描: {} - {}", file.getPath(), e.getMessage());
            return fallbackReview(result, file, "LLM 调用失败: " + e.getMessage());
        }

        try {
            parseLlmResponse(result, response);
            log.info("Review 完成: {} verdict={} issues={}",
                    file.getPath(), result.getVerdict(), result.getIssues().size());
        } catch (Exception e) {
            log.warn("Review JSON 解析失败，降级规则扫描: {}", e.getMessage());
            return fallbackReview(result, file, response);
        }
        return result;
    }

    /** 解析 LLM 返回的 JSON 到 ReviewResult。 */
    @SuppressWarnings("unchecked")
    private void parseLlmResponse(ReviewResult result, String response) throws Exception {
        String json = cleanJson(response);
        Map<String, Object> map = objectMapper.readValue(json, Map.class);
        result.setVerdict(asString(map.get("verdict")));
        result.setSummary(asString(map.get("summary")));

        Object issuesObj = map.get("issues");
        if (issuesObj instanceof List<?> list) {
            List<ReviewIssue> issues = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> im)) continue;
                ReviewIssue issue = new ReviewIssue();
                issue.setDimension(asString(im.get("dimension")));
                issue.setSeverity(Severity.safeParse(asString(im.get("severity"))));
                issue.setLine(asInt(im.get("line")));
                issue.setMessage(asString(im.get("message")));
                issue.setSuggestion(asString(im.get("suggestion")));
                issues.add(issue);
            }
            result.setIssues(issues);
        }
        if (result.getVerdict() == null) {
            result.setVerdict(deriveVerdict(result.getIssues()));
        }
    }

    /**
     * 降级：基于关键词的规则扫描（LLM 不可用/解析失败时）。
     * 至少能识别 SQL 注入、硬编码密钥等明显模式。
     */
    private ReviewResult fallbackReview(ReviewResult result, DiffFile file, String raw) {
        List<ReviewIssue> issues = new ArrayList<>();
        for (String added : file.getAddedLines()) {
            detectByKeyword(added, issues);
        }
        result.setIssues(issues);
        result.setVerdict(deriveVerdict(issues));
        result.setSummary(issues.isEmpty()
                ? "规则扫描未发现问题" + (raw != null && !raw.isBlank() ? "（LLM降级）" : "")
                : "规则扫描发现 " + issues.size() + " 处疑似问题（LLM降级）");
        return result;
    }

    /** 关键词规则扫描（供 Controller 在降级路径调用）。 */
    public List<ReviewIssue> scanByRules(DiffFile file) {
        List<ReviewIssue> issues = new ArrayList<>();
        int lineNo = 1;
        for (String added : file.getAddedLines()) {
            detectByKeyword(added, issues);
            lineNo++;
        }
        return issues;
    }

    private void detectByKeyword(String code, List<ReviewIssue> issues) {
        String lower = code.toLowerCase();
        if (lower.matches(".*\".*select|insert|update|delete.*\".*\\+.*") || lower.contains("statement.execute(\"")) {
            issues.add(new ReviewIssue("SQL注入", Severity.CRITICAL, null,
                    "疑似字符串拼接SQL", "使用预编译参数化查询"));
        }
        if (lower.matches(".*(password|passwd|secret|api[-_]?key|token)\\s*=\\s*\"[^\"]+\".*")) {
            issues.add(new ReviewIssue("硬编码密钥", Severity.CRITICAL, null,
                    "疑似硬编码敏感凭证", "从环境变量/配置中心读取"));
        }
    }

    /** 根据问题严重度推导文件结论。 */
    private String deriveVerdict(List<ReviewIssue> issues) {
        if (issues == null || issues.isEmpty()) return "PASS";
        boolean hasCritical = issues.stream().anyMatch(i -> i.getSeverity() == Severity.CRITICAL);
        if (hasCritical) return "BLOCK";
        return "NEED_FIX";
    }

    /** 把 DiffFile 格式化为 LLM 可读文本（含行号）。 */
    private String formatDiffForLlm(DiffFile file) {
        StringBuilder sb = new StringBuilder();
        int line = 1;
        for (String raw : file.getRawDiff()) {
            sb.append(line++).append(": ").append(raw).append("\n");
        }
        return sb.toString();
    }

    private String cleanJson(String s) {
        if (s == null) return "{}";
        String c = s.trim();
        if (c.startsWith("```json")) c = c.substring(7);
        else if (c.startsWith("```")) c = c.substring(3);
        if (c.endsWith("```")) c = c.substring(0, c.length() - 3);
        return c.trim();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
        }
        return null;
    }
}
