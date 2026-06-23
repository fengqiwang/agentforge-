package com.agentforge.code.gitee;

import com.agentforge.code.review.ReviewIssue;
import com.agentforge.code.review.ReviewResult;
import com.agentforge.code.review.Severity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 把 ReviewResult 渲染为 Gitee PR 评论 Markdown。
 * 含 Severity emoji 标记、文件:行定位、问题描述、修复建议。
 */
@Component
public class CommentBuilder {

    private static final Map<Severity, String> EMOJI = Map.of(
            Severity.CRITICAL, "🔴",
            Severity.WARNING, "🟡",
            Severity.SUGGESTION, "🔵"
    );

    /** 整 PR 汇总评论。 */
    public String buildPrComment(List<ReviewResult> results) {
        long critical = 0, warning = 0, suggestion = 0;
        for (ReviewResult r : results) {
            for (ReviewIssue i : r.getIssues()) {
                if (i.getSeverity() == Severity.CRITICAL) critical++;
                else if (i.getSeverity() == Severity.WARNING) warning++;
                else suggestion++;
            }
        }
        String verdict = critical > 0 ? "🚫 **建议阻塞合并**"
                : warning > 0 ? "⚠️ **建议修复后合并**" : "✅ **通过**";

        StringBuilder sb = new StringBuilder();
        sb.append("## 🤖 AgentForge 代码审查报告\n\n");
        sb.append("**结论：** ").append(verdict).append("\n\n");
        sb.append(String.format("| 严重 🔴 | 警告 🟡 | 建议 🔵 |\n|---|---|---|\n| %d | %d | %d |\n\n",
                critical, warning, suggestion));

        for (ReviewResult r : results) {
            if (r.getIssues() == null || r.getIssues().isEmpty()) continue;
            sb.append("### `").append(r.getFile()).append("`\n\n");
            for (ReviewIssue i : r.getIssues()) {
                sb.append("- ").append(EMOJI.get(i.getSeverity()))
                        .append(" **[").append(i.getSeverity()).append("] ")
                        .append(i.getDimension()).append("**");
                if (i.getLine() != null) {
                    sb.append(" — `").append(r.getFile()).append(":").append(i.getLine()).append("`");
                }
                sb.append("\n  ").append(i.getMessage()).append("\n");
                if (i.getSuggestion() != null && !i.getSuggestion().isBlank()) {
                    sb.append("  > 💡 ").append(i.getSuggestion()).append("\n");
                }
            }
            sb.append("\n");
        }
        sb.append("---\n_由 AgentForge 自动生成 · 请优先处理 🔴 严重项_");
        return sb.toString();
    }

    /** 单条行级评论（用于 Gitee line comment API）。 */
    public String buildLineComment(ReviewIssue issue) {
        return EMOJI.get(issue.getSeverity()) + " **[" + issue.getSeverity() + "] "
                + issue.getDimension() + "**\n\n" + issue.getMessage()
                + (issue.getSuggestion() != null && !issue.getSuggestion().isBlank()
                ? "\n\n> 💡 " + issue.getSuggestion() : "");
    }
}
