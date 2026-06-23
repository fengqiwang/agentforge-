package com.agentforge.workflow.pipeline;

import com.agentforge.common.model.ReportConfig;
import com.agentforge.common.model.SqlGenerationResult;
import com.agentforge.common.model.ValidationResult;
import com.agentforge.report.sql.ISqlExecutionService;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 管道上下文，贯穿 Pipeline 各站传递数据。
 * 注意：不生成 equals/hashCode（包含可变集合 {@link #executedAgents}）。
 */
@Getter
@Setter
@ToString
public class AgentContext {
    // ===== 输入 =====
    private String sessionId;
    private String userQuestion;
    private String enhancedQuestion;
    private String intent;          // REPORT / ORDER / ANALYSIS / CODE / UNKNOWN
    private Double routerConfidence;

    // ===== Schema =====
    private String schemaHints;

    // ===== SQL 链 =====
    private SqlGenerationResult sqlGenerationResult;
    private String generatedSql;
    private String validatedSql;
    private String sqlLevel;
    private String sqlExplanation;
    private Double sqlConfidence;
    private ValidationResult validationResult;

    // ===== 执行 =====
    private ISqlExecutionService.ExecutionResult queryResult;
    private List<Map<String, Object>> resultRows;
    private Integer resultCount;

    // ===== 审查/修复 =====
    private String reviewNote;
    private boolean needRetry;
    private int retryCount;
    private String errorMessage;

    // ===== 格式化 =====
    private ReportConfig formattedResponse;

    // ===== Pipeline 元信息 =====
    private long startTime;
    private final List<String> executedAgents = new ArrayList<>();
}