package com.agentforge.web.controller;

  import com.agentforge.framework.prompt.PromptManager;
  import com.agentforge.report.sql.SqlFixer;
  import lombok.RequiredArgsConstructor;
  import org.springframework.web.bind.annotation.*;

  import java.util.LinkedHashMap;
  import java.util.Map;

  @RestController
  @RequestMapping("/api/test")
  @RequiredArgsConstructor
  public class SqlFixTestController {

      private final SqlFixer sqlFixer;
      private final PromptManager promptManager;

      @PostMapping("/sql-fix")
      public Map<String, Object> fixSql(@RequestBody Map<String, String> request) {
          String sql = request.get("sql");
          String error = request.get("error");

          SqlFixer.SqlFixResult result = sqlFixer.fix(sql, error);

          Map<String, Object> response = new LinkedHashMap<>();
          response.put("originalSql", sql);
          response.put("errorMessage", error);
          response.put("fixedSql", result.getFixedSql());
          response.put("level", result.getLevel());
          response.put("success", result.isSuccess());
          response.put("description", result.getFixDescription());
          return response;
      }

      @GetMapping("/prompts")
      public Map<String, Object> listPrompts() {
          Map<String, Object> response = new LinkedHashMap<>();
          response.put("templates", promptManager.getTemplateNames());
          return response;
      }

      @PostMapping("/prompts/reload")
      public Map<String, Object> reloadPrompts() {
          promptManager.reload();
          Map<String, Object> response = new LinkedHashMap<>();
          response.put("reloaded", true);
          response.put("templates", promptManager.getTemplateNames());
          return response;
      }
  }