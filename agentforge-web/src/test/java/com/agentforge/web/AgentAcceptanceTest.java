package com.agentforge.web;

import com.agentforge.common.model.ValidationResult;
import com.agentforge.workflow.agent.FormatterAgent;
import com.agentforge.workflow.agent.RouterAgent;
import com.agentforge.workflow.agent.SqlFixerAgent;
import com.agentforge.workflow.pipeline.AgentContext;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Week 7 验收测试：P2-06 条件分支 + P2-07 重试配置 + Router 关键词路由
 */
@SpringBootTest
class AgentAcceptanceTest {

    @Autowired RouterAgent routerAgent;
    @Autowired SqlFixerAgent fixerAgent;
    @Autowired FormatterAgent formatterAgent;
    @Autowired RetryRegistry retryRegistry;
    @Autowired CircuitBreakerRegistry cbRegistry;

    // ============ P2-06 条件分支：Fixer 修复错字段 ============

    @Test
    void p2_06_fixerShouldRewriteKnownBadTableName() {
        AgentContext ctx = new AgentContext();
        ctx.setGeneratedSql("SELECT * FROM syb_transuminfo LIMIT 1");
        ctx.setValidationResult(ValidationResult.fail("表 'syb_transuminfo' 不在白名单中"));
        ctx.setSessionId("test-p206-table");

        fixerAgent.execute(ctx);

        assertNotEquals("SELECT * FROM syb_transuminfo LIMIT 1", ctx.getGeneratedSql(),
                "Fixer 没改写表名");
        System.out.println("[P2-06] 表名修复后 SQL: " + ctx.getGeneratedSql());
    }

    @Test
    void p2_06_fixerShouldRewriteKnownBadFieldName() {
        AgentContext ctx = new AgentContext();
        ctx.setGeneratedSql("SELECT SUM(tranamount) FROM syb_transuminfor");
        ctx.setValidationResult(ValidationResult.fail("Unknown column 'tranamount'"));
        ctx.setSessionId("test-p206-field");

        fixerAgent.execute(ctx);

        assertNotEquals("SELECT SUM(tranamount) FROM syb_transuminfor", ctx.getGeneratedSql(),
                "Fixer 没改写字段名");
        System.out.println("[P2-06] 字段修复后 SQL: " + ctx.getGeneratedSql());
    }

    // ============ P2-07 重试配置加载 ============

    @Test
    void p2_07_resilience4jRetryConfigLoaded() {
        io.github.resilience4j.retry.Retry llm = retryRegistry.retry("llm");
        assertNotNull(llm, "llm retry 没注册");
        assertEquals(3, llm.getRetryConfig().getMaxAttempts(), "max-attempts 应为 3");
        System.out.println("[P2-07] LLM Retry maxAttempts=" + llm.getRetryConfig().getMaxAttempts());

        io.github.resilience4j.retry.Retry sql = retryRegistry.retry("sql-executor");
        assertNotNull(sql, "sql-executor retry 没注册");
    }

    @Test
    void p2_07_resilience4jCircuitBreakerConfigLoaded() {
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb = cbRegistry.circuitBreaker("llm");
        assertNotNull(cb);
        assertEquals(50f, cb.getCircuitBreakerConfig().getFailureRateThreshold(), 0.01);
        long slowCallSecs = cb.getCircuitBreakerConfig().getSlowCallDurationThreshold().getSeconds();
        assertTrue(slowCallSecs <= 30, "slow-call-duration 应 ≤ 30s，实际=" + slowCallSecs);
        System.out.println("[P2-08] LLM CB failureRate=" +
                cb.getCircuitBreakerConfig().getFailureRateThreshold() +
                "% slowCall=" + slowCallSecs + "s");
    }

    // ============ Router 关键词路由 ============

    @Test
    void routerKeywordShouldClassifyReportIntent() {
        AgentContext ctx = new AgentContext();
        ctx.setUserQuestion("查询总交易额");
        routerAgent.execute(ctx);
        assertEquals("REPORT", ctx.getIntent());
        System.out.println("[Router] 「查询总交易额」 → " + ctx.getIntent());
    }

    @Test
    void routerKeywordShouldClassifyOrderIntent() {
        AgentContext ctx = new AgentContext();
        ctx.setUserQuestion("最近的工单");
        routerAgent.execute(ctx);
        assertEquals("ORDER", ctx.getIntent());
        System.out.println("[Router] 「最近的工单」 → " + ctx.getIntent());
    }

    @Test
    void routerKeywordShouldClassifyAnalysisIntent() {
        AgentContext ctx = new AgentContext();
        ctx.setUserQuestion("分析商户流失");
        routerAgent.execute(ctx);
        assertEquals("ANALYSIS", ctx.getIntent());
        System.out.println("[Router] 「分析商户流失」 → " + ctx.getIntent());
    }

    @Test
    void routerKeywordShouldClassifyCodeIntent() {
        AgentContext ctx = new AgentContext();
        ctx.setUserQuestion("写个 Python 脚本");
        routerAgent.execute(ctx);
        assertEquals("CODE", ctx.getIntent());
        System.out.println("[Router] 「写个 Python 脚本」 → " + ctx.getIntent());
    }

    // ============ Formatter 健壮性 ============

    @Test
    void formatterShouldHandleNullRows() {
        AgentContext ctx = new AgentContext();
        ctx.setUserQuestion("测试");
        ctx.setValidatedSql("SELECT 1");
        ctx.setResultRows(null);
        assertDoesNotThrow(() -> formatterAgent.execute(ctx));
        assertNotNull(ctx.getFormattedResponse());
        System.out.println("[Formatter] null rows 兜底成功");
    }
}
