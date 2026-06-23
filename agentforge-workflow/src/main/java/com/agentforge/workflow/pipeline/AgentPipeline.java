package com.agentforge.workflow.pipeline;

import com.agentforge.framework.metrics.ReportMetrics;
import com.agentforge.workflow.agent.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPipeline {

    private final RouterAgent routerAgent;
    private final SchemaAgent schemaAgent;
    private final SqlGeneratorAgent sqlGeneratorAgent;
    private final SqlValidatorAgent sqlValidatorAgent;
    private final SqlExecutorAgent sqlExecutorAgent;
    private final SqlFixerAgent sqlFixerAgent;
    private final ReviewAgent reviewAgent;
    private final FormatterAgent formatterAgent;
    private final OrderAgent orderAgent;
    private final AnalysisAgent analysisAgent;
    private final ReportMetrics reportMetrics;

    private static final int MAX_FIX_RETRIES = 3;

    /** 主入口：根据意图分发 */
    public void execute(AgentContext ctx) {
        ctx.setStartTime(System.currentTimeMillis());
        boolean success = false;
        try {
            routerAgent.execute(ctx);
            log.info("[Pipeline] 路由完成 intent={} confidence={}", ctx.getIntent(), ctx.getRouterConfidence());

            if ("UNKNOWN".equals(ctx.getIntent())) {
                ctx.setErrorMessage("无法识别您的意图。支持的查询类型：\n"
                        + "📊 数据查询：如「查询上月交易总额」「各城市商户数排名」「收银宝交易笔数」\n"
                        + "📈 数据分析：如「对比本月和上月交易额」「商户流失分析」\n"
                        + "📋 工单查询：如「未处理的工单有哪些」\n"
                        + "💡 提示：请使用具体的业务术语，如「交易金额」「商户数」「通联收益」等");
                return;
            }

            switch (ctx.getIntent()) {
                case "REPORT"   -> runReportChain(ctx);
                case "ORDER"    -> runOrderChain(ctx);
                case "ANALYSIS" -> runAnalysisChain(ctx);
                case "CODE"     -> runReportChain(ctx);   // 暂复用 SQL 链
                default         -> ctx.setErrorMessage("不支持的业务类型: " + ctx.getIntent());
            }
            success = ctx.getErrorMessage() == null;
        } catch (Exception e) {
            log.error("[Pipeline] 执行失败", e);
            ctx.setErrorMessage("Pipeline 执行异常: " + e.getMessage());
        } finally {
            long elapsed = System.currentTimeMillis() - ctx.getStartTime();
            log.info("[Pipeline] 完成 耗时={}ms agents={}", elapsed, ctx.getExecutedAgents());
            // Week 11 监控埋点：记录 Pipeline 耗时与成败（按意图分标签）
            reportMetrics.recordPipeline(elapsed,
                    ctx.getIntent() == null ? "UNKNOWN" : ctx.getIntent(), success);
        }
    }

    /** 报表链：Schema → Generator → Validator → [Fixer] → Executor → Review → Formatter */
    private void runReportChain(AgentContext ctx) {
        schemaAgent.execute(ctx);
        sqlGeneratorAgent.execute(ctx);

        // 条件分支 P2-06：校验失败最多修 3 次
        for (int attempt = 0; attempt < MAX_FIX_RETRIES; attempt++) {
            sqlValidatorAgent.execute(ctx);
            if (ctx.getValidationResult() != null && ctx.getValidationResult().isPassed()) {
                break;
            }
            log.warn("[Pipeline] SQL 校验失败 attempt={} err={}", attempt,
                    ctx.getValidationResult() != null ? ctx.getValidationResult().getErrorMessage() : "null");
            sqlFixerAgent.execute(ctx);
            ctx.setRetryCount(attempt + 1);
        }

        if (ctx.getValidationResult() == null || !ctx.getValidationResult().isPassed()) {
            ctx.setErrorMessage("SQL 校验失败: " + ctx.getValidationResult().getErrorMessage());
            return;
        }

        sqlExecutorAgent.execute(ctx);
        if (ctx.getQueryResult() == null || !ctx.getQueryResult().isSuccess()) {
            ctx.setErrorMessage("SQL 执行失败: " + (ctx.getQueryResult() != null ? ctx.getQueryResult().getError() :
                    "null"));
            return;
        }

        reviewAgent.execute(ctx);
        formatterAgent.execute(ctx);
    }

    /** 工单链：OrderAgent → Review → Formatter */
    private void runOrderChain(AgentContext ctx) {
        orderAgent.execute(ctx);
        if (ctx.getQueryResult() == null || !ctx.getQueryResult().isSuccess()) {
            ctx.setErrorMessage("工单查询失败: " +
                    (ctx.getQueryResult() != null ? ctx.getQueryResult().getError() : "null"));
            return;
        }
        reviewAgent.execute(ctx);
        formatterAgent.execute(ctx);
    }

    /** 分析链：AnalysisAgent（多步 SQL 内部完成）→ Review → Formatter */
    private void runAnalysisChain(AgentContext ctx) {
        analysisAgent.execute(ctx);
        if (ctx.getResultRows() == null || ctx.getResultRows().isEmpty()) {
            ctx.setErrorMessage("分析无结果");
            return;
        }
        reviewAgent.execute(ctx);
        formatterAgent.execute(ctx);
    }
}