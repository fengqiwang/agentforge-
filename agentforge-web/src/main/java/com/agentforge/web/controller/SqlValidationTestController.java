  package com.agentforge.web.controller;

  import com.agentforge.common.model.ValidationResult;
  import com.agentforge.safety.sql.SqlSafetyValidator;
  import com.agentforge.safety.sql.SqlSanitizer;
  import lombok.RequiredArgsConstructor;
  import org.springframework.web.bind.annotation.*;

  import java.util.LinkedHashMap;
  import java.util.Map;

  @RestController
  @RequestMapping("/api/test/sql-safety")
  @RequiredArgsConstructor
  public class SqlValidationTestController {

      private final SqlSafetyValidator validator;
      private final SqlSanitizer sanitizer;

      /**
       * 安全校验测试
       */
      @PostMapping("/validate")
      public Map<String, Object> validate(@RequestBody Map<String, String> request) {
          String sql = request.get("sql");
          ValidationResult result = validator.validate(sql);

          Map<String, Object> response = new LinkedHashMap<>();
          response.put("sql", sql);
          response.put("passed", result.isPassed());
          response.put("riskLevel", result.getRiskLevel());
          response.put("errorMessage", result.getErrorMessage());
          return response;
      }

      /**
       * SQL清洗测试
       */
      @PostMapping("/sanitize")
      public Map<String, Object> sanitize(@RequestBody Map<String, String> request) {
          String sql = request.get("sql");
          String cleaned = sanitizer.sanitize(sql);

          Map<String, Object> response = new LinkedHashMap<>();
          response.put("original", sql);
          response.put("sanitized", cleaned);
          return response;
      }
  }