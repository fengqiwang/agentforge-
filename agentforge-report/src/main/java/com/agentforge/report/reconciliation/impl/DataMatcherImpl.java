package com.agentforge.report.reconciliation.impl;

import com.agentforge.common.model.reconciliation.ReconciliationRecord;
import com.agentforge.common.model.reconciliation.ReconciliationResult;
import com.agentforge.common.model.reconciliation.ReconciliationResult.DiffDetail;
import com.agentforge.report.reconciliation.IDataMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据匹配引擎
 * 作用：将内部交易数据与第三方对账文件逐条比对，输出匹配结果
 *
 * 匹配策略：
 * 1. EXACT（精确匹配）：流水号完全一致
 * 2. FUZZY（模糊匹配）：商户号 + 日期 + 金额一致（无流水号时使用）
 *
 * 匹配结果分类：
 * - MATCHED：完全匹配
 * - AMOUNT_DIFF：金额有差异（流水号相同但金额不同）
 * - ONLY_INTERNAL：仅我方有记录
 * - ONLY_EXTERNAL：仅第三方有记录
 * - DUPLICATE：重复匹配（一个内部记录匹配到多个第三方记录）
 */
@Slf4j
@Service
public class DataMatcherImpl implements IDataMatcher {

    /** 金额容差：差异小于1元视为匹配 */
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("1.00");

    /**
     * 执行匹配
     *
     * @param internalRecords 我方交易记录（从数据库查询）
     * @param externalRecords 第三方对账记录（从文件解析）
     * @param strategy 匹配策略：EXACT / FUZZY
     * @return 匹配结果
     */
    @Override
    public ReconciliationResult match(List<ReconciliationRecord> internalRecords,
                                       List<ReconciliationRecord> externalRecords,
                                       String strategy) {
        log.info("开始匹配：我方 {} 条，第三方 {} 条，策略={}", internalRecords.size(), externalRecords.size(), strategy);

        List<DiffDetail> diffDetails = new ArrayList<>();
        Set<Integer> matchedInternal = new HashSet<>();
        Set<Integer> matchedExternal = new HashSet<>();

        int matchedCount = 0;
        int amountDiffCount = 0;
        int duplicateCount = 0;
        BigDecimal totalDiffAmount = BigDecimal.ZERO;

        // ========== 第一轮：精确匹配（流水号） ==========
        Map<String, List<Integer>> externalByTranno = buildTrannoIndex(externalRecords);

        for (int i = 0; i < internalRecords.size(); i++) {
            ReconciliationRecord internal = internalRecords.get(i);
            if (internal.getTranno() == null || internal.getTranno().isBlank()) continue;

            List<Integer> extIndices = externalByTranno.get(internal.getTranno());
            if (extIndices == null || extIndices.isEmpty()) continue;

            if (extIndices.size() > 1) {
                // 多个第三方记录有相同流水号 → DUPLICATE
                for (int extIdx : extIndices) {
                    ReconciliationRecord external = externalRecords.get(extIdx);
                    diffDetails.add(buildDetail(ReconciliationResult.DUPLICATE, internal, external));
                    matchedInternal.add(i);
                    matchedExternal.add(extIdx);
                }
                duplicateCount += extIndices.size();
                continue;
            }

            int extIdx = extIndices.get(0);
            ReconciliationRecord external = externalRecords.get(extIdx);

            BigDecimal diff = compareAmounts(internal.getAmount(), external.getAmount());

            if (diff.compareTo(AMOUNT_TOLERANCE) <= 0) {
                // 金额一致（容差范围内）
                diffDetails.add(buildDetail(ReconciliationResult.MATCHED, internal, external));
                matchedCount++;
            } else {
                // 金额有差异
                diffDetails.add(buildDetail(ReconciliationResult.AMOUNT_DIFF, internal, external));
                amountDiffCount++;
                totalDiffAmount = totalDiffAmount.add(diff);
            }

            matchedInternal.add(i);
            matchedExternal.add(extIdx);
        }

        // ========== 第二轮：模糊匹配（仅当策略为 FUZZY 时） ==========
        if ("FUZZY".equalsIgnoreCase(strategy)) {
            Map<String, List<Integer>> externalByCombo = buildComboIndex(externalRecords);

            for (int i = 0; i < internalRecords.size(); i++) {
                if (matchedInternal.contains(i)) continue;

                ReconciliationRecord internal = internalRecords.get(i);
                String key = buildComboKey(internal);
                if (key == null) continue;

                List<Integer> extIndices = externalByCombo.get(key);
                if (extIndices == null) continue;

                for (int extIdx : extIndices) {
                    if (matchedExternal.contains(extIdx)) continue;

                    ReconciliationRecord external = externalRecords.get(extIdx);
                    diffDetails.add(buildDetail(ReconciliationResult.MATCHED, internal, external));
                    matchedCount++;
                    matchedInternal.add(i);
                    matchedExternal.add(extIdx);
                    break; // 只匹配第一个未使用的第三方记录
                }
            }
        }

        // ========== 未匹配的我方记录 ==========
        for (int i = 0; i < internalRecords.size(); i++) {
            if (matchedInternal.contains(i)) continue;
            diffDetails.add(buildDetail(ReconciliationResult.ONLY_INTERNAL, internalRecords.get(i), null));
        }

        // ========== 未匹配的第三方记录 ==========
        for (int i = 0; i < externalRecords.size(); i++) {
            if (matchedExternal.contains(i)) continue;
            diffDetails.add(buildDetail(ReconciliationResult.ONLY_EXTERNAL, null, externalRecords.get(i)));
        }

        // ========== 统计汇总 ==========
        BigDecimal internalTotal = internalRecords.stream()
                .map(ReconciliationRecord::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal externalTotal = externalRecords.stream()
                .map(ReconciliationRecord::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ReconciliationResult.builder()
                .totalInternal(internalRecords.size())
                .totalExternal(externalRecords.size())
                .matchedCount(matchedCount)
                .amountDiffCount(amountDiffCount)
                .onlyInternalCount((int) diffDetails.stream()
                        .filter(d -> ReconciliationResult.ONLY_INTERNAL.equals(d.getMatchType()))
                        .count())
                .onlyExternalCount((int) diffDetails.stream()
                        .filter(d -> ReconciliationResult.ONLY_EXTERNAL.equals(d.getMatchType()))
                        .count())
                .internalTotal(internalTotal)
                .externalTotal(externalTotal)
                .diffAmount(totalDiffAmount)
                .diffDetails(diffDetails)
                .build();
    }

    // ==================== 索引构建 ====================

    /** 按流水号建立索引 */
    private Map<String, List<Integer>> buildTrannoIndex(List<ReconciliationRecord> records) {
        Map<String, List<Integer>> index = new HashMap<>();
        for (int i = 0; i < records.size(); i++) {
            String tranno = records.get(i).getTranno();
            if (tranno != null && !tranno.isBlank()) {
                index.computeIfAbsent(tranno.trim(), k -> new ArrayList<>()).add(i);
            }
        }
        return index;
    }

    /** 按商户号+日期+金额建立组合索引 */
    private Map<String, List<Integer>> buildComboIndex(List<ReconciliationRecord> records) {
        Map<String, List<Integer>> index = new HashMap<>();
        for (int i = 0; i < records.size(); i++) {
            String key = buildComboKey(records.get(i));
            if (key != null) {
                index.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
            }
        }
        return index;
    }

    /** 构建组合键：cusid|date|amount */
    private String buildComboKey(ReconciliationRecord record) {
        if (record.getCusid() == null || record.getDate() == null || record.getAmount() == null) {
            return null;
        }
        return record.getCusid() + "|" + normalizeDate(record.getDate()) + "|" + record.getAmount().toPlainString();
    }

    // ==================== 比较方法 ====================

    /** 比较金额差异，返回绝对差值 */
    private BigDecimal compareAmounts(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return new BigDecimal("999999999");
        return a.subtract(b).abs();
    }

    /** 标准化日期格式：去除横线，统一为 YYYYMMDD */
    private String normalizeDate(String date) {
        if (date == null) return "";
        return date.replace("-", "").replace("/", "").replace(".", "").trim();
    }

    /** 构建 DiffDetail */
    private DiffDetail buildDetail(String matchType, ReconciliationRecord internal, ReconciliationRecord external) {
        BigDecimal diffAmount = null;
        if (internal != null && external != null
                && internal.getAmount() != null && external.getAmount() != null) {
            diffAmount = internal.getAmount().subtract(external.getAmount());
        }

        return DiffDetail.builder()
                .matchType(matchType)
                .internalTranno(internal != null ? internal.getTranno() : null)
                .internalCusid(internal != null ? internal.getCusid() : null)
                .internalAmount(internal != null ? internal.getAmount() : null)
                .internalDate(internal != null ? internal.getDate() : null)
                .externalTranno(external != null ? external.getTranno() : null)
                .externalCusid(external != null ? external.getCusid() : null)
                .externalAmount(external != null ? external.getAmount() : null)
                .externalDate(external != null ? external.getDate() : null)
                .diffAmount(diffAmount)
                .build();
    }
}
