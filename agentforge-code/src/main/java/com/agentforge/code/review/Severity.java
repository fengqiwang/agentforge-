package com.agentforge.code.review;

/**
 * Review 问题严重度分级。
 *
 * <ul>
 *   <li>CRITICAL — 安全漏洞（SQL注入、硬编码密钥、XSS）</li>
 *   <li>WARNING — 潜在Bug（空指针、资源泄漏、N+1查询）</li>
 *   <li>SUGGESTION — 规范建议（命名规范、代码风格、可读性）</li>
 * </ul>
 */
public enum Severity {

    CRITICAL("严重", 3),
    WARNING("警告", 2),
    SUGGESTION("建议", 1);

    private final String label;
    private final int weight;

    Severity(String label, int weight) {
        this.label = label;
        this.weight = weight;
    }

    public String getLabel() {
        return label;
    }

    public int getWeight() {
        return weight;
    }

    /** 容错解析：LLM 输出的大小写/中文都能映射。 */
    public static Severity safeParse(String s) {
        if (s == null) return SUGGESTION;
        String lower = s.trim().toUpperCase();
        return switch (lower) {
            case "CRITICAL", "严重", "BLOCKER", "ERROR" -> CRITICAL;
            case "WARNING", "警告", "MAJOR", "WARN" -> WARNING;
            default -> SUGGESTION;
        };
    }
}
