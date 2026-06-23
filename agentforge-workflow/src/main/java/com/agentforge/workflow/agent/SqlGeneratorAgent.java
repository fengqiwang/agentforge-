package com.agentforge.workflow.agent;

  import com.agentforge.common.model.SqlGenerationResult;
  import com.agentforge.report.sql.SqlGeneratorService;
  import com.agentforge.workflow.pipeline.Agent;
  import com.agentforge.workflow.pipeline.AgentContext;
  import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
  import io.github.resilience4j.retry.annotation.Retry;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.util.concurrent.CompletableFuture;

  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class SqlGeneratorAgent implements Agent {

      private final SqlGeneratorService delegate;

      @Override
      public String getName() { return "SqlGeneratorAgent"; }

      /**
       * SQL 生成 Agent — Pipeline 第 2 站（重负载，必须有容错）
       *
       * 职责：
       *  - 调用 SqlGeneratorService 做三级 SQL 生成（模板→Few-Shot→LLM）
       *  - 把生成结果写入 ctx，供 Validator/Executor 使用
       *
       * 输入（从 ctx 读）：
       *  - enhancedQuestion
       *  - sessionId
       *
       * 输出（写到 ctx）：
       *  - sqlGenerationResult   完整生成结果
       *  - generatedSql          SQL 字符串
       *  - sqlLevel              TEMPLATE / FEW_SHOT / LLM
       *  - sqlExplanation        自然语言解释
       *  - sqlConfidence         置信度
       *
       * 容错（覆盖 P2-07 重试 + P2-08 熔断）：
       *  - @Retry(name="llm")                失败重试 3 次，指数退避（1s/2s/4s）
       *  - @CircuitBreaker(name="llm")       失败率 50% 触发熔断，开 60s
       *  - 超时由 LangChain4j ChatModel 自带 timeout(30s) 兜底
       *  - fallback：所有重试 + 熔断全失败时调用，设 errorMessage 而非抛异常
       *
       * 覆盖验收：P2-07（重试）、P2-08（超时降级）
       */
      @Override
      @Retry(name = "llm", fallbackMethod = "fallback")
      @CircuitBreaker(name = "llm")
      public void execute(AgentContext ctx) {
              String question = ctx.getEnhancedQuestion() != null
                      ? ctx.getEnhancedQuestion() : ctx.getUserQuestion();
              log.info("[SqlGeneratorAgent] 开始生成 question={}", question.substring(0, Math.min(50,
  question.length())));

              SqlGenerationResult result = delegate.generate(question, ctx.getSessionId());

              ctx.setSqlGenerationResult(result);
              ctx.setGeneratedSql(result.getSql());
              ctx.setSqlLevel(result.getLevel());
              ctx.setSqlExplanation(result.getExplanation());
              ctx.setSqlConfidence(result.getConfidence());
              ctx.getExecutedAgents().add(getName());
              log.info("[SqlGeneratorAgent] 完成 level={} sql={}", result.getLevel(), result.getSql());

      }

      /** 超时/熔断兜底 */
      private CompletableFuture<Void> fallback(AgentContext ctx, Throwable t) {
          log.error("[SqlGeneratorAgent] 降级: {}", t.getMessage());
          ctx.setErrorMessage("SQL 生成超时或失败，已重试 3 次: " + t.getMessage());
          ctx.setGeneratedSql(null);
          return CompletableFuture.completedFuture(null);
      }
  }