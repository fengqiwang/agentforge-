package com.agentforge.web.controller;

  import com.agentforge.common.model.ReportConfig;
  import com.agentforge.report.builder.ReportBuilderService;
  import lombok.RequiredArgsConstructor;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.*;

  import java.util.List;
  import java.util.Map;

  @RestController
  @RequestMapping("/api/report")
  @RequiredArgsConstructor
  public class ReportController {

      private final ReportBuilderService reportBuilderService;

      /**
       * 从自然语言问题构建报表配置
       * 作用：开发者输入中文问题，返回完整报表配置JSON
       */
      @PostMapping("/build")
      public ResponseEntity<?> buildFromQuestion(@RequestBody Map<String, String> request) {
          String question = request.get("question");
          String sessionId = request.get("sessionId");
          if (question == null || question.isBlank()) {
              return ResponseEntity.badRequest().body(Map.of("error", "问题不能为空"));
          }
          if (sessionId == null || sessionId.isBlank()) {
              ReportConfig config = reportBuilderService.buildFromQuestion(question,sessionId);
              if (config == null) {
                  return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                          .body(Map.of("error", "报表生成失败，请换个描述方式"));
              }
              return ResponseEntity.ok(config);
          }else {
              ReportConfig config = reportBuilderService.buildFromQuestion(question,sessionId);
              if (config == null) {
                  return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                          .body(Map.of("error", "报表生成失败，请换个描述方式"));
              }
              return ResponseEntity.ok(config);
          }
      }

      /**
       * 从已有SQL构建报表配置
       * 作用：开发者审查修改SQL后直接生成报表
       */
      @PostMapping("/build-sql")
      public ResponseEntity<?> buildFromSql(@RequestBody Map<String, String> request) {
          String name = request.get("name");
          String sql = request.get("sqlText");
          if (sql == null || sql.isBlank()) {
              return ResponseEntity.badRequest().body(Map.of("error", "SQL不能为空"));
          }

          ReportConfig config = reportBuilderService.buildFromSql(name, sql);
          if (config == null) {
              return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                      .body(Map.of("error", "报表生成失败"));
          }
          return ResponseEntity.ok(config);
      }

      /**
       * 保存报表配置
       * 作用：保存后生成 shareToken，拼接为分享URL：/report/view/{shareToken}
       */
      @PostMapping("/save")
      public ReportConfig save(@RequestBody ReportConfig config) {
          return reportBuilderService.save(config);
      }

      /**
       * 报表列表
       */
      @GetMapping("/list")
      public List<ReportConfig> list() {
          return reportBuilderService.listReports();
      }

      /**
       * 删除报表
       */
      @DeleteMapping("/{id}")
      public Map<String, Object> delete(@PathVariable Long id) {
          boolean deleted = reportBuilderService.delete(id);
          return Map.of("success", deleted);
      }

      /**
       * 参数化执行SQL（筛选联动）
       * 作用：前端用户修改筛选条件后，将参数替换到SQL模板中重新查询
       */
      @PostMapping("/execute")
      public ResponseEntity<?> executeParamSql(@RequestBody Map<String, Object> request) {
          String sql = (String) request.get("sql");
          @SuppressWarnings("unchecked")
          Map<String, String> params = (Map<String, String>) request.get("params");

          if (sql == null || sql.isBlank()) {
              return ResponseEntity.badRequest().body(Map.of("error", "SQL不能为空"));
          }
          if (params == null) {
              params = Map.of();
          }

          Map<String, Object> result = reportBuilderService.executeParamSql(sql, params);
          return ResponseEntity.ok(result);
      }

      /**
       * 通过分享令牌查看报表（含最新数据）
       * 作用：业务人员通过分享链接访问，无需登录
       */
      @GetMapping("/view/{shareToken}")
      public ResponseEntity<?> view(@PathVariable String shareToken) {
          Map<String, Object> result = reportBuilderService.executeReport(shareToken);
          if (result.containsKey("error") && "报表不存在".equals(result.get("error"))) {
              return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
          }
          return ResponseEntity.ok(result);
      }
  }