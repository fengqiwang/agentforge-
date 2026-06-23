package com.agentforge.common.constant;

import java.time.format.DateTimeFormatter;

/**
 * 日期格式化常量。
 *
 * <p>统一管理项目中所有 DateTimeFormatter，避免零散定义和重复创建。
 * DateTimeFormatter 是线程安全的（Java 8+），可安全定义为静态常量。
 */
public final class DateFormats {

    /** yyyy-MM，用于报告月度标识 */
    public static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    /** yyyyMMdd，用于数据库日期字段和文件名 */
    public static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** yyyy-MM-dd，标准日期展示 */
    public static final DateTimeFormatter STANDARD_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DateFormats() {
        // 工具类，禁止实例化
    }
}
