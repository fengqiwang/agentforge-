package com.agentforge.code.review;

import com.agentforge.code.diff.DiffFile;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单文件代码审查结果。
 *
 * <p>由 {@link CodeReviewer} 对每个 {@link DiffFile} 调用 LLM 产出。
 * 最终汇总为整 PR 的审查报告存入 af_code_review.review_result。
 */
@Data
public class ReviewResult {

    /** 文件路径 */
    private String file;

    /** 文件分类 */
    private String category;

    /** 变更类型 */
    private String changeType;

    /** 变更行数 */
    private int changedLines;

    /** 发现的问题 */
    private List<ReviewIssue> issues = new ArrayList<>();

    /** 文件级别结论：PASS / NEED_FIX / BLOCK */
    private String verdict;

    /** 摘要 */
    private String summary;

    public static ReviewResult of(DiffFile file) {
        ReviewResult r = new ReviewResult();
        r.setFile(file.getPath());
        r.setCategory(file.getCategory());
        r.setChangeType(file.getChangeType());
        r.setChangedLines(file.changedLineCount());
        return r;
    }
}
