package com.agentforge.common.model.reconciliation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 对账结果模型
 * 作用：汇总对账匹配的整体结果，包含统计数字和差异明细
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationResult {

    /** 对账批次ID */
    private Long reconciliationId;

    /** 我方总笔数 */
    private int totalInternal;

    /** 第三方总笔数 */
    private int totalExternal;

    /** 匹配成功数 */
    private int matchedCount;

    /** 金额差异数 */
    private int amountDiffCount;

    /** 仅我方有 */
    private int onlyInternalCount;

    /** 仅第三方有 */
    private int onlyExternalCount;

    /** 我方总金额 */
    private BigDecimal internalTotal;

    /** 第三方总金额 */
    private BigDecimal externalTotal;

    /** 差异金额（绝对值之和） */
    private BigDecimal diffAmount;

    /** 差异明细列表 */
    private List<DiffDetail> diffDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffDetail {
        /** 匹配类型 */
        private String matchType;

        /** 我方流水号 */
        private String internalTranno;
        private String internalCusid;
        private BigDecimal internalAmount;
        private String internalDate;

        /** 第三方流水号 */
        private String externalTranno;
        private String externalCusid;
        private BigDecimal externalAmount;
        private String externalDate;

        /** 差异金额 */
        private BigDecimal diffAmount;
    }

    /** 匹配类型枚举 */
    public static final String MATCHED = "MATCHED";
    public static final String AMOUNT_DIFF = "AMOUNT_DIFF";
    public static final String ONLY_INTERNAL = "ONLY_INTERNAL";
    public static final String ONLY_EXTERNAL = "ONLY_EXTERNAL";
    public static final String DUPLICATE = "DUPLICATE";
}
