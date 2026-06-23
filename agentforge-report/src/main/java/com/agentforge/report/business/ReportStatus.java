package com.agentforge.report.business;

/**
 * 报告状态枚举。
 *
 * <p>替代各处硬编码的 {@code "GENERATING"} / {@code "COMPLETED"} / {@code "FAILED"} 魔法字符串。
 * 数据库存储使用 {@link #getCode()}，代码逻辑使用枚举比较。
 */
public enum ReportStatus {

    GENERATING("GENERATING", "生成中"),
    COMPLETED("COMPLETED", "已完成"),
    FAILED("FAILED", "执行失败");

    private final String code;
    private final String desc;

    ReportStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据数据库存储的 code 反查枚举。
     *
     * @param code 状态码
     * @return 对应的枚举值
     * @throws IllegalArgumentException 未知状态码
     */
    public static ReportStatus fromCode(String code) {
        for (ReportStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知的报告状态: " + code);
    }
}
