package com.agentforge.code.review;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 单条审查意见。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewIssue {

    /** 问题维度：命名规范 / 空指针风险 / 资源泄漏 / SQL注入 / N+1查询 / 硬编码密钥 / 其他 */
    private String dimension;

    /** 严重度 */
    private Severity severity;

    /** 涉及行号（估算，可能为 null） */
    private Integer line;

    /** 问题描述 */
    private String message;

    /** 修复建议 */
    private String suggestion;
}
