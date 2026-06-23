package com.agentforge.common.model.reconciliation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 列映射配置
 * 作用：第三方文件的列名不固定，通过此配置将外部列名映射到内部标准字段
 *
 * 使用流程：
 * 1. 上传文件后，系统自动识别列名并生成建议映射（autoMapping）
 * 2. 用户可手动调整映射（manualMapping）
 * 3. 确认后传给匹配引擎使用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMapping {

    /** 内部标准字段 → 第三方文件中的列名 */
    private Map<String, String> mapping;

    /** 文件的实际列名列表（解析后自动填充） */
    private String[] fileColumns;

    /**
     * 自动映射：按关键词匹配文件列名到标准字段
     * 关键词映射规则：
     * - tranno ← 流水号/transaction_id/trans_no/交易流水
     * - cusid ← 商户号/merchant_id/cus_id/商户编号
     * - amount ← 金额/amount/交易金额/tranamt
     * - date ← 日期/date/交易日期/tran_date/settledate
     * - status ← 状态/status/交易状态/tran_stat
     */
    public static ColumnMapping autoDetect(String[] fileColumns) {
        Map<String, String> mapping = new LinkedHashMap<>();

        String[][] keywords = {
                {"tranno", "流水号|transaction_id|trans_no|交易流水|tranno|order_no"},
                {"cusid", "商户号|merchant_id|cus_id|商户编号|cusid|merchant_no"},
                {"amount", "金额|amount|交易金额|tranamt|trade_amount|实收金额"},
                {"date", "日期|date|交易日期|tran_date|settledate|trade_date|trans_date|清算日期|settle_date"},
                {"status", "状态|status|交易状态|tran_stat|trade_status|trans_status"}
        };

        for (String[] entry : keywords) {
            String field = entry[0];
            String[] kws = entry[1].split("\\|");
            for (String col : fileColumns) {
                String normalized = col.trim().toLowerCase();
                for (String kw : kws) {
                    if (normalized.contains(kw.toLowerCase())) {
                        mapping.put(field, col);
                        break;
                    }
                }
                if (mapping.containsKey(field)) break;
            }
        }

        return ColumnMapping.builder()
                .mapping(mapping)
                .fileColumns(fileColumns)
                .build();
    }
}
