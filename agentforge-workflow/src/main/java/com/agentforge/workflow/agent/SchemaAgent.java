package com.agentforge.workflow.agent;

  import com.agentforge.framework.rag.SchemaRetriever;
  import com.agentforge.report.sql.ContextBuilder;
  import com.agentforge.workflow.pipeline.Agent;
  import com.agentforge.workflow.pipeline.AgentContext;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

@Slf4j
  @Component
  @RequiredArgsConstructor
  public class SchemaAgent implements Agent {

      private final ContextBuilder contextBuilder;
      private final SchemaRetriever schemaRetriever;

      @Override
      public String getName() { return "SchemaAgent"; }

    /**
     * Schema Agent — Pipeline 第 1 站
     *
     * 职责：
     *  - 调用 ContextBuilder 注入追问上下文（拼成 enhancedQuestion）
     *  - 调用 SchemaRetriever 做 RAG 检索，找出问题相关的表
     *
     * 输入（从 ctx 读）：
     *  - userQuestion
     *  - sessionId
     *
     * 输出（写到 ctx）：
     *  - enhancedQuestion    注入上下文后的完整问题
     *  - schemaHints         相关表清单（含 score 和注释），后续 LLM 用
     *
     * 容错：
     *  - 无注解（RAG 检索走 embedding，本身有 timeout，失败时 schemaHints 降级为空字符串，不阻断链路）
     */
      @Override
      public void execute(AgentContext ctx) {
          // 1. 追问上下文注入（如果 sessionId 非空）
          String contextHints = "";
          if (ctx.getSessionId() != null && !ctx.getSessionId().isBlank()) {
              contextHints = contextBuilder.buildContext(ctx.getSessionId(), ctx.getUserQuestion());
          }
          ctx.setEnhancedQuestion(contextHints.isBlank()
                  ? ctx.getUserQuestion()
                  : ctx.getUserQuestion() + "\n\n[上下文信息]" + contextHints);

          // 2. RAG 检索相关表
          try {
              var matches =
                      schemaRetriever.retrieve(ctx.getUserQuestion(), 5);
              StringBuilder sb = new StringBuilder();
              sb.append("相关表：\n");
              for (var m : matches) {
                  sb.append("- ").append(m.getTableName())
                    .append(" (").append(m.getScore()).append("): ")
                    .append(m.getTableComment()).append("\n");
              }
              ctx.setSchemaHints(sb.toString());
              log.info("[SchemaAgent] 检索到 {} 张相关表", matches.size());
          } catch (Exception e) {
              log.warn("[SchemaAgent] 检索失败，降级为空 hints: {}", e.getMessage());
              ctx.setSchemaHints("");
          }

          ctx.getExecutedAgents().add(getName());
      }
  }