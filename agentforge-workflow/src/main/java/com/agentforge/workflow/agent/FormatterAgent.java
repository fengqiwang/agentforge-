package com.agentforge.workflow.agent;

  import com.agentforge.common.model.ReportConfig;
  import com.agentforge.report.builder.ChartRecommender;
  import com.agentforge.report.builder.DataTypeDetector;
  import com.agentforge.report.builder.FilterPanelGenerator;
  import com.agentforge.workflow.pipeline.Agent;
  import com.agentforge.workflow.pipeline.AgentContext;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.util.List;
  import java.util.Map;

  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class FormatterAgent implements Agent {

      private final DataTypeDetector dataTypeDetector;
      private final ChartRecommender chartRecommender;
      private final FilterPanelGenerator filterPanelGenerator;

      @Override
      public String getName() { return "FormatterAgent"; }

      /**
       * 格式化 Agent — Pipeline 第 7 站（终站）
       *
       * 职责：
       *  - 列类型检测（DataTypeDetector）
       *  - 图表推荐（ChartRecommender，根据列类型 + 数据形状推 number_card / bar / line）
       *  - 筛选面板生成（FilterPanelGenerator，从 WHERE 提取可变字段）
       *  - 组装 ReportConfig 写入 ctx
       *
       * 输入（从 ctx 读）：
       *  - resultRows
       *  - validatedSql
       *  - userQuestion
       *
       * 输出（写到 ctx）：
       *  - formattedResponse    最终 ReportConfig（前端可直接渲染）
       *
       * 容错：
       *  - 纯本地算法，无 I/O，不需要容错
       *  - 列检测 / 图表推荐内部失败时返回 null，不影响 ReportConfig 主体
       *
       * 覆盖验收：无（不在 P2-05~08）
       */
      @Override
      public void execute(AgentContext ctx) {
          List<Map<String, Object>> rows = ctx.getResultRows();
          if (rows == null || rows.isEmpty()) {
              log.warn("[FormatterAgent] 无数据可格式化");
              ctx.setFormattedResponse(ReportConfig.builder()
                      .name(ctx.getUserQuestion())
                      .sqlText(ctx.getValidatedSql())
                      .build());
              ctx.getExecutedAgents().add(getName());
              return;
          }

          // 1. 列类型检测
          var columns = dataTypeDetector.detect(rows);

          // 2. 图表推荐
          var chartConfig = chartRecommender.recommend(ctx.getValidatedSql(), columns, rows);

          // 3. 筛选面板
          var filters = filterPanelGenerator.generate(ctx.getValidatedSql(), columns);

          // 4. 组装 ReportConfig
          ReportConfig config = ReportConfig.builder()
                  .name(ctx.getUserQuestion())
                  .sqlText(ctx.getValidatedSql())
                  .chartConfig(chartConfig)
                  .filterConfig(filters)
                  .columnConfig(columns)
                  .sampleRows(rows.size() > 100 ? rows.subList(0, 100) : rows)
                  .build();

          ctx.setFormattedResponse(config);
          ctx.getExecutedAgents().add(getName());
          log.info("[FormatterAgent] 完成 chart={} filters={} columns={}",
                  chartConfig != null ? chartConfig.getType() : "null",
                  filters != null ? filters.size() : 0,
                  columns.size());
      }
  }