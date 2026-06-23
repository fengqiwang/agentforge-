package com.agentforge.report.builder.impl;

import com.agentforge.common.model.ReportConfig;
import com.agentforge.common.model.ReportConfig.*;
import com.agentforge.common.model.SqlGenerationResult;
import com.agentforge.report.builder.IReportBuilderService;
import com.agentforge.report.sql.ISqlExecutionService;
import com.agentforge.report.sql.ISqlGeneratorService;
import com.agentforge.report.builder.DataTypeDetector;
import com.agentforge.report.builder.ChartRecommender;
import com.agentforge.report.builder.FilterPanelGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportBuilderServiceImpl implements IReportBuilderService {

    private final ISqlGeneratorService generatorService;
    private final ISqlExecutionService executionService;
    private final DataTypeDetector dataTypeDetector;
    private final ChartRecommender chartRecommender;
    private final FilterPanelGenerator filterPanelGenerator;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;




    /**
     * 方式1：从自然语言问题构建报表
     * 作用：开发者输入中文问题，一键生成完整报表配置
     * 流程：问题 → SQL生成 → 执行 → 列检测 → 图表推荐 → 筛选生成 → 组装
     */
    @Override
    public ReportConfig buildFromQuestion(String question,String sessionId) {
        log.info("从问题构建报表：{}", question);

        SqlGenerationResult generated = generatorService.generate(question,sessionId);
        if (!generated.isMatched() || generated.getSql() == null || generated.getSql().isBlank()) {
            log.warn("SQL生成失败");
            return null;
        }

        return doBuild(question, generated.getSql());
    }

    /**
     * 方式2：从已有SQL构建报表
     * 作用：开发者手动审查修改SQL后，直接生成报表
     */
    @Override
    public ReportConfig buildFromSql(String name, String sql) {
        log.info("从SQL构建报表：{}", name);
        return doBuild(name, sql);
    }

    /**
     * 保存报表配置到数据库
     * 作用：开发者确认报表后保存，生成分享链接给业务人员
     * 使用 KeyHolder 获取自增ID，避免并发问题
     */
    @Override
    public ReportConfig save(ReportConfig config) {
        if (config.getShareToken() == null) {
            config.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        }

        String chartJson = toJson(config.getChartConfig());
        String filterJson = toJson(config.getFilterConfig());
        String columnJson = toJson(config.getColumnConfig());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO af_report_config (name, description, sql_text, chart_config, filter_config, column_config, created_by, share_token) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, config.getName());
            ps.setString(2, config.getDescription());
            ps.setString(3, config.getSqlText());
            ps.setString(4, chartJson);
            ps.setString(5, filterJson);
            ps.setString(6, columnJson);
            ps.setString(7, config.getCreatedBy());
            ps.setString(8, config.getShareToken());
            return ps;
        }, keyHolder);

        Long id = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
        config.setId(id);

        log.info("报表已保存：id={}, name={}, shareToken={}", id, config.getName(), config.getShareToken());
        return config;
    }

    /**
     * 通过分享令牌查询报表配置
     * 作用：业务人员通过分享链接访问报表
     */
    @Override
    public ReportConfig getByShareToken(String shareToken) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM af_report_config WHERE share_token = ? AND status = 1",
                shareToken
        );
        if (rows.isEmpty()) return null;
        return mapToReportConfig(rows.get(0));
    }

    /**
     * 查询报表列表
     * 作用：开发者管理已保存的报表
     */
    @Override
    public List<ReportConfig> listReports() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, name, description, created_by, share_token, status, created_at " +
                        "FROM af_report_config ORDER BY created_at DESC"
        );

        List<ReportConfig> reports = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            ReportConfig config = new ReportConfig();
            config.setId(((Number) row.get("id")).longValue());
            config.setName((String) row.get("name"));
            config.setDescription((String) row.get("description"));
            config.setCreatedBy((String) row.get("created_by"));
            config.setShareToken((String) row.get("share_token"));
            config.setStatus((Integer) row.get("status"));
            reports.add(config);
        }
        return reports;
    }

    /**
     * 删除报表
     */
    @Override
    public boolean delete(Long id) {
        int affected = jdbcTemplate.update("DELETE FROM af_report_config WHERE id = ?", id);
        return affected > 0;
    }

    /**
     * 参数化执行SQL（筛选联动）
     * 作用：前端用户修改筛选条件后，将参数替换到SQL模板中重新执行
     * 流程：接收原始SQL + 参数Map → 替换占位符 → 安全执行 → 返回结果行
     */
    @Override
    public Map<String, Object> executeParamSql(String sqlTemplate, Map<String, String> params) {
        String sql = sqlTemplate;

        // 日期筛选参数 key 映射：FilterPanel 发出的 settledate_start/end → SQL 模板的 start_date/end_date
        Map<String, String> normalizedParams = new LinkedHashMap<>(params);
        if (normalizedParams.containsKey("settledate_start") && !normalizedParams.containsKey("start_date")) {
            normalizedParams.put("start_date", normalizedParams.get("settledate_start"));
        }
        if (normalizedParams.containsKey("settledate_end") && !normalizedParams.containsKey("end_date")) {
            normalizedParams.put("end_date", normalizedParams.get("settledate_end"));
        }

        for (Map.Entry<String, String> entry : normalizedParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value != null && !value.isBlank()) {
                sql = sql.replace("{" + key + "}", value);
            }
        }

        log.info("参数化执行SQL：原始模板长度={}，参数数={}", sqlTemplate.length(), params.size());

        ISqlExecutionService.ExecutionResult execResult = executionService.execute(sql);

        Map<String, Object> result = new LinkedHashMap<>();
        if (execResult.isSuccess() && execResult.getRows() != null) {
            result.put("rows", execResult.getRows());
            result.put("rowCount", execResult.getRows().size());
        } else {
            result.put("rows", List.of());
            result.put("rowCount", 0);
            result.put("error", execResult.getError());
        }
        return result;
    }

    /**
     * 执行报表SQL并返回数据
     * 作用：前端打开报表时查询最新数据
     */
    @Override
    public Map<String, Object> executeReport(String shareToken) {
        ReportConfig config = getByShareToken(shareToken);
        if (config == null) {
            return Map.of("error", "报表不存在");
        }

        ISqlExecutionService.ExecutionResult execResult = executionService.execute(config.getSqlText());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report", config);
        if (execResult.isSuccess() && execResult.getRows() != null) {
            result.put("rows", execResult.getRows());
            result.put("rowCount", execResult.getRows().size());
        } else {
            result.put("rows", List.of());
            result.put("rowCount", 0);
            result.put("error", execResult.getError());
        }
        return result;
    }

    // ==================== 核心构建逻辑 ====================

    /**
     * 内部构建：SQL → 执行 → 列检测 → 图表推荐 → 筛选生成 → 组装
     */
    private ReportConfig doBuild(String name, String sql) {
        // 1. 执行SQL
        ISqlExecutionService.ExecutionResult execResult = executionService.execute(sql);

        if (!execResult.isSuccess() || execResult.getRows() == null || execResult.getRows().isEmpty()) {
            log.warn("SQL执行失败或结果为空");
            return ReportConfig.builder()
                    .name(name)
                    .sqlText(sql)
                    .build();
        }

        List<Map<String, Object>> rows = execResult.getRows();

        // 2. 检测列类型
        //    自动识别每列是文本/数值/金额/百分比/日期
        List<ColumnConfig> columnConfigs = dataTypeDetector.detect(rows);

        // 3. 推荐图表
        //    根据数据形状自动推荐最合适的图表类型
        ChartConfig chartConfig = chartRecommender.recommend(sql, columnConfigs, rows);

        // 4. 生成筛选面板
        //    从WHERE条件提取可变参数，生成筛选控件
        List<FilterConfig> filterConfigs = filterPanelGenerator.generate(sql, columnConfigs);

        // 5. 组装
        ReportConfig reportConfig = ReportConfig.builder()
                .name(name)
                .sqlText(sql)
                .chartConfig(chartConfig)
                .filterConfig(filterConfigs)
                .columnConfig(columnConfigs)
                .sampleRows(rows.size() > 100 ? rows.subList(0, 100) : rows)
                .build();

        log.info("报表构建完成：{} 列，{} 行，图表={}，筛选={}",
                columnConfigs.size(), rows.size(),
                chartConfig != null ? chartConfig.getType() : "无",
                filterConfigs.size());

        return reportConfig;
    }

    // ==================== JSON工具 ====================

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON序列化失败", e);
            return null;
        }
    }

    private ReportConfig mapToReportConfig(Map<String, Object> row) {
        return ReportConfig.builder()
                .id(((Number) row.get("id")).longValue())
                .name((String) row.get("name"))
                .description((String) row.get("description"))
                .sqlText((String) row.get("sql_text"))
                .chartConfig(fromJson((String) row.get("chart_config"), ChartConfig.class))
                .filterConfig(fromJsonList((String) row.get("filter_config"), FilterConfig.class))
                .columnConfig(fromJsonList((String) row.get("column_config"), ColumnConfig.class))
                .createdBy((String) row.get("created_by"))
                .shareToken((String) row.get("share_token"))
                .status(row.get("status") != null ? ((Number) row.get("status")).intValue() : 1)
                .build();
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("JSON反序列化失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化JSON列表 — 使用传入的class参数，不再硬编码
     */
    private <T> List<T> fromJsonList(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            log.error("JSON列表反序列化失败：{}", e.getMessage());
            return null;
        }
    }
}
