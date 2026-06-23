package com.agentforge.workflow.agent;

  import com.agentforge.workflow.pipeline.Agent;
  import com.agentforge.workflow.pipeline.AgentContext;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.math.BigDecimal;
  import java.util.List;
  import java.util.Map;

  @Slf4j
  @Component
  public class ReviewAgent implements Agent {

      @Override
      public String getName() { return "ReviewAgent"; }

      /**
       * 结果审查 Agent — Pipeline 第 6 站
       *
       * 职责：
       *  - 空结果检测（可能过滤条件过严）
       *  - 异常大额检测（金额 > 1 亿）
       *  - 结果集过大警告（> 1 万行）
       *
       * 输入（从 ctx 读）：
       *  - resultRows
       *  - resultCount
       *
       * 输出（写到 ctx）：
       *  - reviewNote     审查意见（多个换行），为空表示一切正常
       *  - needRetry      是否需要重新生成（暂未自动触发，仅打标）
       *
       * 容错：
       *  - 纯内存计算，无 I/O，不需要容错
       *  - 数值解析异常被 NumberFormatException 兜底，跳过该字段
       *
       * 覆盖验收：无（结果质量保证，不在 P2-05~08）
       */
      @Override
      public void execute(AgentContext ctx) {
          if (ctx.getResultRows() == null) {
              ctx.setReviewNote("无结果数据");
              return;
          }

          StringBuilder note = new StringBuilder();

          // 1. 空结果检测
          if (ctx.getResultRows().isEmpty()) {
              note.append("查询结果为空，可能：\n")
                  .append("  - 时间范围内无数据\n")
                  .append("  - 过滤条件过严\n")
                  .append("  - 字段名不匹配\n");
          }

          // 2. 异常大额检测（找名字里带 amount/amt/fee 的列）
          for (Map<String, Object> row : ctx.getResultRows().subList(0, Math.min(3, ctx.getResultRows().size()))) {
              for (Map.Entry<String, Object> e : row.entrySet()) {
                  String col = e.getKey().toLowerCase();
                  if ((col.contains("amount") || col.contains("amt") || col.contains("fee"))
                          && e.getValue() instanceof Number num) {
                      BigDecimal v = new BigDecimal(num.toString());
                      if (v.compareTo(new BigDecimal("100000000")) > 0) {
                          note.append("检测到异常大额: ").append(col).append("=").append(v).append("\n");
                      }
                  }
              }
          }

          // 3. 数量异常（超过 1 万行）
          if (ctx.getResultCount() != null && ctx.getResultCount() > 10000) {
              note.append("结果集过大: ").append(ctx.getResultCount())
                  .append(" 行，建议增加 LIMIT 或时间范围\n");
          }

          if (note.length() > 0) {
              ctx.setReviewNote(note.toString());
              ctx.setNeedRetry(true);
              log.info("[ReviewAgent] 发现异常:\n{}", note);
          } else {
              log.info("[ReviewAgent] 一切正常");
          }
          ctx.getExecutedAgents().add(getName());
      }
  }