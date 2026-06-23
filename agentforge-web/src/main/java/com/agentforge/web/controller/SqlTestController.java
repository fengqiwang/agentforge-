 package com.agentforge.web.controller;

  import com.agentforge.common.model.SqlGenerationResult;
  import com.agentforge.report.sql.ISqlExecutionService;
  import com.agentforge.report.sql.ISqlGeneratorService;
  import lombok.RequiredArgsConstructor;
  import org.springframework.web.bind.annotation.*;

  import java.util.*;
  import java.util.concurrent.CompletableFuture;
  import java.util.concurrent.LinkedBlockingQueue;
  import java.util.concurrent.ThreadPoolExecutor;
  import java.util.concurrent.TimeUnit;
  import java.util.concurrent.ConcurrentHashMap;

  @RestController
  @RequestMapping("/api/sql")
  @RequiredArgsConstructor
  public class SqlTestController {

      private final ISqlGeneratorService generatorService;
      private final ISqlExecutionService executionService;

      /** 测试任务专用线程池（有界队列 + CallerRunsPolicy 防 OOM） */
      private static final ThreadPoolExecutor TEST_EXECUTOR = new ThreadPoolExecutor(
              1, 2, 60L, TimeUnit.SECONDS,
              new LinkedBlockingQueue<>(10),
              new ThreadPoolExecutor.CallerRunsPolicy());

      /** 存储批量测试结果（有界缓存） */
      private final Map<String, Map<String, Object>> testResults = new ConcurrentHashMap<>();

      /**
       * 单题：生成SQL
       */
      @PostMapping("/generate")
      public SqlGenerationResult generate(@RequestBody Map<String, String> request) {
          return generatorService.generate(request.get("question"));
      }

      /**
       * 单题：生成 + 执行
       */
      @PostMapping("/execute")
      public Map<String, Object> execute(@RequestBody Map<String, String> request) {
          String question = request.get("question");
          SqlGenerationResult generated = generatorService.generate(question);
          return executionService.generateAndExecute(question, generated);
      }

      /**
       * 30题批量测试（只生成，不执行）—— 同步，通常 < 30秒
       */
      @GetMapping("/batch-test")
      public Map<String, Object> batchTest() {
          List<String> questions = get30Questions();

          List<Map<String, Object>> results = new ArrayList<>();
          int success = 0;
          Map<String, Integer> levelCount = new LinkedHashMap<>();
          levelCount.put("TEMPLATE", 0);
          levelCount.put("FEW_SHOT", 0);
          levelCount.put("LLM", 0);

          for (String question : questions) {
              long start = System.currentTimeMillis();
              SqlGenerationResult result = generatorService.generate(question);
              long duration = System.currentTimeMillis() - start;

              boolean sqlValid = result.isMatched()
                      && result.getSql() != null
                      && !result.getSql().isBlank()
                      && result.getSql().toUpperCase().contains("SELECT");

              Map<String, Object> item = new LinkedHashMap<>();
              item.put("question", question);
              item.put("level", result.getLevel());
              item.put("sql", result.getSql());
              item.put("confidence", result.getConfidence());
              item.put("explanation", result.getExplanation());
              item.put("valid", sqlValid);
              item.put("durationMs", duration);
              results.add(item);

              if (sqlValid) success++;
              if (result.getLevel() != null) {
                  levelCount.merge(result.getLevel(), 1, Integer::sum);
              }
          }

          Map<String, Object> summary = new LinkedHashMap<>();
          summary.put("total", questions.size());
          summary.put("success", success);
          summary.put("rate", String.format("%.1f%%", success * 100.0 / questions.size()));
          summary.put("levelDistribution", levelCount);
          summary.put("results", results);
          return summary;
      }

      /**
       * 30题全量测试（生成 + 执行）—— 异步，避免HTTP超时
       */
      @PostMapping("/full-test")
      public Map<String, String> startFullTest() {
          String testId = UUID.randomUUID().toString().substring(0, 8);
          testResults.put(testId, Map.of("status", "RUNNING", "total", 30));

          CompletableFuture.runAsync(() -> runFullTest(testId), TEST_EXECUTOR);

          return Map.of("testId", testId, "status", "RUNNING",
                  "pollUrl", "/api/sql/full-test/" + testId);
      }

      /**
       * 查询全量测试结果
       */
      @GetMapping("/full-test/{testId}")
      public Map<String, Object> getFullTestResult(@PathVariable String testId) {
          return testResults.getOrDefault(testId, Map.of("error", "testId不存在"));
      }

      private void runFullTest(String testId) {
          List<String> questions = get30Questions();
          List<Map<String, Object>> results = new ArrayList<>();
          int executed = 0;
          Map<String, Integer> levelCount = new LinkedHashMap<>();
          levelCount.put("TEMPLATE", 0);
          levelCount.put("FEW_SHOT", 0);
          levelCount.put("LLM", 0);

          for (String question : questions) {
              try {
                  SqlGenerationResult generated = generatorService.generate(question);
                  Map<String, Object> execResult = executionService.generateAndExecute(question, generated);
                  results.add(execResult);

                  if (Boolean.TRUE.equals(execResult.get("success"))) executed++;
                  if (generated.getLevel() != null) {
                      levelCount.merge(generated.getLevel(), 1, Integer::sum);
                  }
              } catch (Exception e) {
                  Map<String, Object> errorItem = new LinkedHashMap<>();
                  errorItem.put("question", question);
                  errorItem.put("success", false);
                  errorItem.put("error", e.getMessage());
                  results.add(errorItem);
              }
          }

          Map<String, Object> summary = new LinkedHashMap<>();
          summary.put("status", "COMPLETED");
          summary.put("total", questions.size());
          summary.put("executed", executed);
          summary.put("executeRate", String.format("%.1f%%", executed * 100.0 / questions.size()));
          summary.put("levelDistribution", levelCount);
          summary.put("results", results);

          testResults.put(testId, summary);
      }

      // ==================== 30道测试题 ====================

      private List<String> get30Questions() {
          return List.of(

              // ===== 简单题 × 15（应命中模板 TEMPLATE） =====
              "交易总额是多少",
              "交易有多少笔",
              "商户总数",
              "收银宝总交易额是多少",
              "按月统计交易额",
              "交易成功率是多少",
              "南昌市交易总额",
              "赣州市交易笔数",
              "各城市交易额排名",
              "商户数量按城市统计",
              "每天的收银宝交易额走势",
              "各交易类型的交易笔数和金额",
              "各商户类型的数量统计",
              "今天的通联收益是多少",
              "本月手续费总收入",

              // ===== 中等题 × 10（应走 Few-Shot） =====
              "九江市的交易成功率是多少",
              "交易金额最大的前10笔交易",
              "各城市商户数和交易额的统计",
              "商户名称和对应的交易总额",
              "南昌市上个月的交易笔数",
              "赣州市手续费收入",
              "各商户类型的手续费总额",
              "每个商户类型的平均单笔交易金额",
              "提现失败有多少笔",
              "最近7天的收益趋势",

              // ===== 复杂题 × 5（应走 LLM） =====
              "交易额连续增长的城市有哪些",
              "上个月有交易但本月没有的流失商户",
              "交易额占比与商户数量占比差异最大的城市",
              "各城市交易额环比增长率排名",
              "交易活跃度最高的前5个商户及其交易明细"
          );
      }
  }