package com.agentforge.common.model.reconciliation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 统一的对账记录模型
 * 内部数据和第三方数据都解析为此格式，便于匹配引擎比较
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationRecord {

    /** 流水号 */
    private String tranno;

    /** 商户号 */
    private String cusid;

    /** 交易金额 */
    private BigDecimal amount;

    /** 交易日期（原始字符串，可能是 YYYYMMDD 或 yyyy-MM-dd） */
    private String date;

    /** 交易状态 */
    private String status;

    /** 行号（原始文件中的行号，方便定位） */
    private int rowNumber;

    /** 来源：INTERNAL / EXTERNAL */
    private String source;
}
