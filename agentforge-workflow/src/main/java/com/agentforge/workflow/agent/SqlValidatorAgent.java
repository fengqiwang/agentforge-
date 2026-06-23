package com.agentforge.workflow.agent;

  import com.agentforge.common.model.ValidationResult;
  import com.agentforge.safety.sql.SqlSafetyValidator;
  import com.agentforge.workflow.pipeline.Agent;
  import com.agentforge.workflow.pipeline.AgentContext;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

@Slf4j
  @Component
  @RequiredArgsConstructor
  public class SqlValidatorAgent implements Agent {

      private final SqlSafetyValidator safetyValidator;

      @Override
      public String getName() { return "SqlValidatorAgent"; }

    /**
     * SQL 校验 Agent — Pipeline 第 3 站
     *
     * 职责：
     *  - 用 JSqlParser AST 做安全校验（SELECT-only、表白名单、子查询深度、JOIN 数）
     *  - 校验失败把 errorMessage 写入 ctx，触发 Fixer 条件分支
     *
     * 输入（从 ctx 读）：
     *  - generatedSql
     *
     * 输出（写到 ctx）：
     *  - validationResult   校验结果（passed + errorMessage）
     *  - validatedSql       校验通过后等于 generatedSql；不通过为 null
     *  - needRetry          是否需要触发 SqlFixer
     *
     * 容错：
     *  - 纯本地 AST 解析，无 I/O，不需要重试
     *  - 解析异常被 SqlSafetyValidator 内部 catch，转成 ValidationResult.fail
     *
     * 覆盖验收：无（校验本身不参与 P2-05~08）
     */
      @Override
      public void execute(AgentContext ctx) {
          if (ctx.getGeneratedSql() == null || ctx.getGeneratedSql().isBlank()) {
              ctx.setValidationResult(ValidationResult.fail("生成的 SQL 为空"));
              ctx.setNeedRetry(true);
              ctx.getExecutedAgents().add(getName());
          }

          ValidationResult result = safetyValidator.validate(ctx.getGeneratedSql());
          ctx.setValidationResult(result);
          ctx.setNeedRetry(!result.isPassed());

          if (result.isPassed()) {
              ctx.setValidatedSql(ctx.getGeneratedSql());
              log.info("[SqlValidatorAgent] 通过");
          } else {
              log.warn("[SqlValidatorAgent] 拒绝: {}", result.getErrorMessage());
          }
          ctx.getExecutedAgents().add(getName());
      }
  }