package com.agentforge.report.reconciliation;

import com.agentforge.common.model.reconciliation.ColumnMapping;
import com.agentforge.common.model.reconciliation.ReconciliationRecord;
import com.agentforge.common.model.reconciliation.ReconciliationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对账总入口
 * 作用：编排文件解析 → 列映射 → 数据查询 → 匹配 → 持久化的完整流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final FileUploadService fileUploadService;
    private final DataMatcher dataMatcher;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 上传对账文件并预览
     * 作用：返回解析后的列名和前10行数据，以及自动检测的列映射建议
     */
    public Map<String, Object> uploadAndPreview(MultipartFile file) {
        FileUploadService.ParseResult parseResult = fileUploadService.parse(file);
        ColumnMapping autoMapping = fileUploadService.detectColumnMapping(parseResult);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", parseResult.columns);
        result.put("rowCount", parseResult.rows.size());
        result.put("previewRows", parseResult.rows.stream().limit(10).toList());
        result.put("autoMapping", autoMapping.getMapping());

        return result;
    }

    /**
     * 执行对账匹配
     * 作用：确认列映射后，查询内部数据 → 与第三方数据匹配 → 保存结果
     */
    public ReconciliationResult executeMatch(String name, MultipartFile file,
                                              Map<String, String> columnMapping,
                                              String matchStrategy) {
        // 1. 解析文件
        FileUploadService.ParseResult parseResult = fileUploadService.parse(file);

        // 2. 用用户确认的列映射转换第三方记录
        ColumnMapping mapping = ColumnMapping.builder()
                .mapping(columnMapping)
                .fileColumns(parseResult.columns)
                .build();
        List<ReconciliationRecord> externalRecords =
                fileUploadService.mapToRecords(parseResult.rows, mapping, "EXTERNAL");

        // 3. 查询内部数据
        List<ReconciliationRecord> internalRecords = queryInternalData(externalRecords);
        log.info("查询到内部数据 {} 条", internalRecords.size());

        // 4. 创建对账批次记录
        Long batchId = createBatch(name, file.getOriginalFilename(), matchStrategy);

        // 5. 执行匹配
        ReconciliationResult result = dataMatcher.match(internalRecords, externalRecords, matchStrategy);
        result.setReconciliationId(batchId);

        // 6. 保存匹配明细
        saveDetails(batchId, result.getDiffDetails());

        // 7. 更新批次汇总
        updateBatchSummary(batchId, result);

        log.info("对账完成：id={}, 匹配={}, 差异={}, 仅我方={}, 仅第三方={}",
                batchId, result.getMatchedCount(), result.getAmountDiffCount(),
                result.getOnlyInternalCount(), result.getOnlyExternalCount());

        return result;
    }

    public List<Map<String, Object>> listBatches() {
        return jdbcTemplate.queryForList(
                "SELECT id, name, source_file, match_strategy, total_internal, total_external, " +
                        "matched_count, amount_diff_count, only_internal_count, only_external_count, " +
                        "diff_amount, status, created_by, created_at " +
                        "FROM af_reconciliation ORDER BY created_at DESC");
    }

    public Map<String, Object> getBatch(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM af_reconciliation WHERE id = ?", id);
        return rows.isEmpty() ? Map.of("error", "对账记录不存在") : rows.get(0);
    }

    public boolean deleteBatch(Long id) {
        int batchDeleted = jdbcTemplate.update("DELETE FROM af_reconciliation WHERE id = ?", id);
        jdbcTemplate.update("DELETE FROM af_reconciliation_detail WHERE reconciliation_id = ?", id);
        return batchDeleted > 0;
    }

    public Map<String, Object> getDetails(Long batchId, String type, int page, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM af_reconciliation_detail WHERE reconciliation_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(batchId);

        if (type != null && !type.isBlank()) {
            sql.append(" AND match_type = ?");
            params.add(type);
        }

        String countSql = "SELECT COUNT(*) FROM af_reconciliation_detail WHERE reconciliation_id = ?";
        int total;
        if (type != null && !type.isBlank()) {
            countSql += " AND match_type = ?";
            total = jdbcTemplate.queryForObject(countSql, Integer.class, batchId, type);
        } else {
            total = jdbcTemplate.queryForObject(countSql, Integer.class, batchId);
        }

        sql.append(" ORDER BY id LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        List<Map<String, Object>> details = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("details", details);
        return result;
    }

    /**
     * 根据批次ID重建完整的ReconciliationResult（用于导出/摘要）
     */
    public ReconciliationResult getResult(Long batchId) {
        Map<String, Object> batch = getBatch(batchId);
        if (batch.containsKey("error")) return null;

        List<Map<String, Object>> detailRows = jdbcTemplate.queryForList(
                "SELECT * FROM af_reconciliation_detail WHERE reconciliation_id = ?",
                batchId);

        List<ReconciliationResult.DiffDetail> details = detailRows.stream()
                .map(row -> ReconciliationResult.DiffDetail.builder()
                        .matchType((String) row.get("match_type"))
                        .internalTranno((String) row.get("internal_tranno"))
                        .internalCusid((String) row.get("internal_cusid"))
                        .internalAmount(row.get("internal_amount") != null
                                ? new BigDecimal(row.get("internal_amount").toString()) : null)
                        .internalDate((String) row.get("internal_date"))
                        .externalTranno((String) row.get("external_tranno"))
                        .externalCusid((String) row.get("external_cusid"))
                        .externalAmount(row.get("external_amount") != null
                                ? new BigDecimal(row.get("external_amount").toString()) : null)
                        .externalDate((String) row.get("external_date"))
                        .diffAmount(row.get("diff_amount") != null
                                ? new BigDecimal(row.get("diff_amount").toString()) : null)
                        .build())
                .collect(Collectors.toList());

        return ReconciliationResult.builder()
                .reconciliationId(batchId)
                .totalInternal(toInt(batch.get("total_internal")))
                .totalExternal(toInt(batch.get("total_external")))
                .matchedCount(toInt(batch.get("matched_count")))
                .amountDiffCount(toInt(batch.get("amount_diff_count")))
                .onlyInternalCount(toInt(batch.get("only_internal_count")))
                .onlyExternalCount(toInt(batch.get("only_external_count")))
                .diffAmount(batch.get("diff_amount") != null
                        ? new BigDecimal(batch.get("diff_amount").toString()) : BigDecimal.ZERO)
                .diffDetails(details)
                .build();
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        return Integer.parseInt(val.toString());
    }

    // ==================== 内部方法 ====================

    /**
     * 从数据库查询内部交易数据
     * syb_transuminfor 是汇总表，没有 tranno 列
     * 使用 cusid + settledate + tranamt 做匹配
     */
    private List<ReconciliationRecord> queryInternalData(List<ReconciliationRecord> externalRecords) {
        Set<String> dates = new HashSet<>();
        Set<String> cusids = new HashSet<>();
        for (ReconciliationRecord r : externalRecords) {
            if (r.getDate() != null) dates.add(normalizeDate(r.getDate()));
            if (r.getCusid() != null) cusids.add(r.getCusid());
        }

        if (dates.isEmpty() && cusids.isEmpty()) {
            return jdbcTemplate.query(
                    "SELECT cusid, tranamt, settledate FROM syb_transuminfor " +
                            "WHERE settledate >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 7 DAY), '%Y%m%d') " +
                            "LIMIT 5000",
                    (rs, rowNum) -> ReconciliationRecord.builder()
                            .cusid(rs.getString("cusid"))
                            .amount(rs.getBigDecimal("tranamt"))
                            .date(rs.getString("settledate"))
                            .source("INTERNAL")
                            .build()
            );
        }

        StringBuilder sql = new StringBuilder(
                "SELECT cusid, tranamt, settledate FROM syb_transuminfor WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (!dates.isEmpty()) {
            sql.append(" AND settledate IN (")
                    .append(String.join(",", dates.stream().map(d -> "?").toList()))
                    .append(")");
            params.addAll(dates);
        }

        if (!cusids.isEmpty()) {
            String placeholders = cusids.stream().map(c -> "?").collect(Collectors.joining(","));
            sql.append(" AND cusid IN (").append(placeholders).append(")");
            params.addAll(cusids);
        }

        sql.append(" LIMIT 5000");

        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> ReconciliationRecord.builder()
                        .cusid(rs.getString("cusid"))
                        .amount(rs.getBigDecimal("tranamt"))
                        .date(rs.getString("settledate"))
                        .source("INTERNAL")
                        .build(),
                params.toArray()
        );
    }

    private Long createBatch(String name, String sourceFile, String matchStrategy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO af_reconciliation (name, source_file, match_strategy, status) VALUES (?, ?, ?, 'PROCESSING')",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name);
            ps.setString(2, sourceFile);
            ps.setString(3, matchStrategy);
            return ps;
        }, keyHolder);

        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }

    private void saveDetails(Long batchId, List<ReconciliationResult.DiffDetail> details) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO af_reconciliation_detail " +
                        "(reconciliation_id, match_type, internal_tranno, internal_cusid, internal_amount, internal_date, " +
                        "external_tranno, external_cusid, external_amount, external_date, diff_amount) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                details.stream().map(d -> new Object[]{
                        batchId, d.getMatchType(),
                        d.getInternalTranno(), d.getInternalCusid(), d.getInternalAmount(), d.getInternalDate(),
                        d.getExternalTranno(), d.getExternalCusid(), d.getExternalAmount(), d.getExternalDate(),
                        d.getDiffAmount()
                }).toList()
        );
    }

    private void updateBatchSummary(Long batchId, ReconciliationResult result) {
        jdbcTemplate.update(
                "UPDATE af_reconciliation SET " +
                        "total_internal = ?, total_external = ?, matched_count = ?, " +
                        "amount_diff_count = ?, only_internal_count = ?, only_external_count = ?, " +
                        "diff_amount = ?, status = 'COMPLETED' WHERE id = ?",
                result.getTotalInternal(), result.getTotalExternal(), result.getMatchedCount(),
                result.getAmountDiffCount(), result.getOnlyInternalCount(), result.getOnlyExternalCount(),
                result.getDiffAmount(), batchId
        );
    }

    private String normalizeDate(String date) {
        if (date == null) return "";
        return date.replace("-", "").replace("/", "").replace(".", "").trim();
    }
}
