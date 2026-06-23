package com.agentforge.workflow.agent;

  import com.agentforge.report.sql.SqlFixer;
  import com.agentforge.workflow.pipeline.Agent;
  import com.agentforge.workflow.pipeline.AgentContext;
  import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
  import io.github.resilience4j.retry.annotation.Retry;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class SqlFixerAgent implements Agent {

      private final SqlFixer sqlFixer;

      @Override
      public String getName() { return "SqlFixerAgent"; }

      /**
       * SQL 修复 Agent — Pipeline 第 4 站（条件分支触发）
       *
       * 职责：
       *  - 接收 Validator 拒绝的 SQL + 错误信息
       *  - 调用 SqlFixer 做两级修复：Levenshtein 快修 → LLM 修复
       *  - 修复后写回 ctx.generatedSql，下一轮 Validator 重校验
       *
       * 输入（从 ctx 读）：
       *  - generatedSql           原始 SQL（可能错误）
       *  - validationResult       校验失败的错误信息
       *
       * 输出（写到 ctx）：
       *  - generatedSql           修复后的 SQL（覆盖原值）
       *
       * 容错（覆盖 P2-06 条件分支）：
       *  - @Retry(name="llm")                LLM 修复失败重试 3 次
       *  - @CircuitBreaker(name="llm")       与 SqlGeneratorAgent 共享熔断器
       *  - Pipeline 层面：最多调用 3 次（AgentPipeline.MAX_FIX_RETRIES=3）
       *  - 修复失败不抛异常，让下一轮 Validator 继续判
       *
       * 覆盖验收：P2-06（条件分支，校验失败→修复）
       */
      @Override
      @Retry(name = "llm", fallbackMethod = "fallback")
      @CircuitBreaker(name = "llm")
      public void execute(AgentContext ctx) {
          String sql = ctx.getGeneratedSql();
          String err = ctx.getValidationResult() != null
                  ? ctx.getValidationResult().getErrorMessage() : "unknown";

          log.info("[SqlFixerAgent] 尝试修复 err={}", err);
          try {
              SqlFixer.SqlFixResult fixResult = sqlFixer.fix(sql, err);
              if (fixResult.isSuccess()) {
                  ctx.setGeneratedSql(fixResult.getFixedSql());
                  log.info("[SqlFixerAgent] 修复成功 level={} desc={}",
                          fixResult.getLevel(), fixResult.getFixDescription());
              } else {
                  log.warn("[SqlFixerAgent] 修复失败: {}", fixResult.getFixDescription());
                  // 校验失败信息保留，下一轮 validator 会继续判
              }
          } catch (Exception e) {
              log.error("[SqlFixerAgent] 异常", e);
          }
          ctx.getExecutedAgents().add(getName());
      }
  }