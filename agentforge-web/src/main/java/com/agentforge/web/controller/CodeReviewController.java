package com.agentforge.web.controller;

import com.agentforge.web.service.CodeReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 代码审查 API：历史列表、详情、手动触发。
 */
@RestController
@RequestMapping("/api/code-review")
@RequiredArgsConstructor
public class CodeReviewController {

    private final CodeReviewService codeReviewService;

    /** Review 历史列表。 */
    @GetMapping("/history")
    public List<Map<String, Object>> history(@RequestParam(defaultValue = "20") int limit) {
        return codeReviewService.listRecent(limit);
    }

    /** 单条 Review 详情。 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return codeReviewService.getById(id);
    }

    /**
     * 手动触发审查。body: {repoUrl, prNumber, commitId, diff, postComment}
     * 始终返回 markdownComment；postComment=true 时尝试发 Gitee 评论。
     */
    @PostMapping("/trigger")
    public Map<String, Object> trigger(@RequestBody Map<String, Object> body) {
        String repoUrl = (String) body.getOrDefault("repoUrl", "manual/trigger");
        Integer prNumber = body.get("prNumber") instanceof Number n ? n.intValue()
                : (body.get("prNumber") instanceof String s && !s.isBlank() ? Integer.valueOf(s) : null);
        String commitId = (String) body.get("commitId");
        String diff = (String) body.get("diff");
        boolean post = Boolean.TRUE.equals(body.get("postComment"));
        if (diff == null || diff.isBlank()) {
            return Map.of("error", "缺少 diff");
        }
        return codeReviewService.reviewStoreAndComment(repoUrl, prNumber, commitId, diff, post);
    }
}
