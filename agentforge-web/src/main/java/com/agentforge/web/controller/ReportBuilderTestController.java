 package com.agentforge.web.controller;

  import com.agentforge.common.model.ReportConfig.*;
  import com.agentforge.common.model.SqlGenerationResult;
  import com.agentforge.report.builder.ChartRecommender;
  import com.agentforge.report.builder.DataTypeDetector;
  import com.agentforge.report.sql.ISqlExecutionService;
  import com.agentforge.report.sql.ISqlGeneratorService;
  import lombok.RequiredArgsConstructor;
  import org.springframework.web.bind.annotation.*;

  import java.util.*;

  @RestController
  @RequestMapping("/api/test/report-builder")
  @RequiredArgsConstructor
  public class ReportBuilderTestController {

      private final DataTypeDetector dataTypeDetector;
      private final ChartRecommender chartRecommender;
      private final ISqlGeneratorService generatorService;
      private final ISqlExecutionService executionService;

      /**
       * 端到端：问题 → SQL → 执行 → 检测类型 → 推荐图表
       */
      @PostMapping("/analyze")
      public Map<String, Object> analyze(@RequestBody Map<String, String> request) {
          String question = request.get("question");

          Map<String, Object> result = new LinkedHashMap<>();
          result.put("question", question);

          // 1. 生成SQL
          SqlGenerationResult generated = generatorService.generate(question);
          result.put("sql", generated.getSql());
          result.put("level", generated.getLevel());

          if (!generated.isMatched() || generated.getSql() == null || generated.getSql().isBlank()) {
              result.put("error", "SQL生成失败");
              return result;
          }

          // 2. 执行SQL
          Map<String, Object> execResult = executionService.generateAndExecute(question, generated);

          if (!Boolean.TRUE.equals(execResult.get("success"))) {
              result.put("error", "SQL执行失败：" + execResult.get("error"));
              return result;
          }

          @SuppressWarnings("unchecked")
          List<Map<String, Object>> rows = (List<Map<String, Object>>) execResult.get("rows");

          if (rows == null || rows.isEmpty()) {
              result.put("error", "查询结果为空");
              return result;
          }

          // 3. 检测列类型
          List<ColumnConfig> columnConfigs = dataTypeDetector.detect(rows);

          // 4. 推荐图表
          ChartConfig chartConfig = chartRecommender.recommend(generated.getSql(), columnConfigs, rows);

          // 5. 组装输出
          result.put("rowCount", rows.size());
          result.put("columns", columnConfigs);
          result.put("chart", chartConfig);
          result.put("sampleRows", rows.stream().limit(5).toList());
          return result;
      }
  }