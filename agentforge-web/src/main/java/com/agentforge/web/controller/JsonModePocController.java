package com.agentforge.web.controller;

import com.agentforge.common.model.IntentResult;
import com.agentforge.common.model.SqlGenerationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/poc")
@Slf4j
public class JsonModePocController {

      private final ChatModel chatModel;
      private final ObjectMapper objectMapper;

      public JsonModePocController(ChatModel chatModel, ObjectMapper objectMapper) {
          this.chatModel = chatModel;
          this.objectMapper = objectMapper;
      }

      /**
       * 测试1：意图分类（Router Agent的核心能力）
       */
      @GetMapping("/intent")
      public ResponseEntity<?> testIntent(@RequestParam String question) {
          String systemPrompt = """
                  你是一个意图分类器。根据用户问题，判断意图类型。
                  只返回JSON，不要返回任何其他内容。
                  JSON格式：{"intent":"...","confidence":0.0,"reasoning":"..."}

                  intent取值：
                  - REPORT：查询数据、统计分析、报表
                  - ORDER：工单查询、工单处理
                  - ANALYSIS：趋势分析、对比分析、同比环比
                  - CODE：代码审查、Code Review

                  示例：
                  问题："上个月交易总额多少" → {"intent":"REPORT","confidence":0.95,"reasoning":"用户查询交易数据汇总"}
                  """;

          String userMessage = "问题：\"" + question + "\"\n\n只返回JSON。";

          ChatResponse response = chatModel.chat(
                  SystemMessage.from(systemPrompt),
                  UserMessage.from(userMessage)
          );

          String raw = response.aiMessage().text();
          log.info("意图分类原始返回: {}", raw);

          try {
              String json = extractJson(raw);
              IntentResult result = objectMapper.readValue(json, IntentResult.class);
              return ResponseEntity.ok(Map.of(
                      "success", true,
                      "parsed", result,
                      "raw", raw
              ));
          } catch (Exception e) {
              log.error("JSON解析失败: {}", raw, e);
              return ResponseEntity.ok(Map.of(
                      "success", false,
                      "error", e.getMessage(),
                      "raw", raw
              ));
          }
      }

      /**
       * 测试2：SQL生成（SQL Agent的核心能力）
       */
      @GetMapping("/sql")
      public ResponseEntity<?> testSql(@RequestParam String question) {
          String systemPrompt = """
                  你是一个SQL生成器。根据用户问题和表结构，生成MySQL查询语句。
                  只返回JSON，不要返回任何其他内容。
                  JSON格式：{"sql":"...","explanation":"...","tables":["..."]}

                  可用表：
                  - syb_transuminfor：收银宝交易汇总表（cusid商户号, tranamt交易金额, amount笔数, transtype交易类型, settledate结算日期int yyyyMMdd, allinpayfee/oldallinpayfee通联收益）
                  - tlt_transuminfor：收付通交易汇总表（同上，需排除4类transtype）
                  - syb_merchant：商户表（cusid商户号, cusname名称, state状态, city城市, custype类型, regdate注册日期varchar yyyy-MM-dd, one邮政标识）
                  - syb_merchantattribute：商户归属绑定（cusid, organtbstart拓展部门, organtbend维护部门, startrate/endrate/allocationrate分润比例）
                  - sys_dept：部门表（dept_id, dept_name, status='0'正常, del_flag='0'存在）

                  示例：
                  问题："查询上个月交易总额" → {"sql":"SELECT SUM(tranamt) AS total FROM syb_transuminfor WHERE settledate BETWEEN 20260501 AND 20260531","explanation":"按int日期范围汇总交易额","tables":["syb_transuminfor"]}
                  """;

          String userMessage = "问题：\"" + question + "\"\n\n只返回JSON。";

          ChatResponse response = chatModel.chat(
                  SystemMessage.from(systemPrompt),
                  UserMessage.from(userMessage)
          );

          String raw = response.aiMessage().text();
          log.info("SQL生成原始返回: {}", raw);

          try {
              String json = extractJson(raw);
              SqlGenerationResult result = objectMapper.readValue(json, SqlGenerationResult.class);
              return ResponseEntity.ok(Map.of(
                      "success", true,
                      "parsed", result,
                      "raw", raw
              ));
          } catch (Exception e) {
              log.error("JSON解析失败: {}", raw, e);
              return ResponseEntity.ok(Map.of(
                      "success", false,
                      "error", e.getMessage(),
                      "raw", raw
              ));
          }
      }

      /**
       * 批量测试：用5个问题验证JSON输出稳定性
       */
      @GetMapping("/batch")
      public ResponseEntity<?> batchTest() {
          String[] questions = {
                  "上个月交易总额多少",
                  "查询所有停用的商户",
                  "最近一周交易趋势",
                  "帮我看看这个PR的代码质量",
                  "3月份北京分公司的工单处理情况"
          };

          List<Map<String, Object>> results = new ArrayList<>();
          int successCount = 0;

          for (String q : questions) {
              long start = System.currentTimeMillis();
              try {
                  String systemPrompt = """
                          判断用户意图，只返回JSON：{"intent":"REPORT|ORDER|ANALYSIS|CODE","confidence":0.0,"reasoning":"..."}
                          REPORT=数据查询, ORDER=工单, ANALYSIS=趋势分析, CODE=代码审查
                          """;
                  ChatResponse response = chatModel.chat(
                          SystemMessage.from(systemPrompt),
                          UserMessage.from("问题：\"" + q + "\"\n\n只返回JSON。")
                  );
                  String raw = response.aiMessage().text();
                  String json = extractJson(raw);
                  IntentResult parsed = objectMapper.readValue(json, IntentResult.class);
                  successCount++;
                  results.add(Map.of(
                          "question", q,
                          "success", true,
                          "intent", parsed.getIntent(),
                          "confidence", parsed.getConfidence(),
                          "duration_ms", System.currentTimeMillis() - start
                  ));
              } catch (Exception e) {
                  results.add(Map.of(
                          "question", q,
                          "success", false,
                          "error", e.getMessage(),
                          "duration_ms", System.currentTimeMillis() - start
                  ));
              }
          }

          return ResponseEntity.ok(Map.of(
                  "total", questions.length,
                  "success", successCount,
                  "success_rate", (successCount * 100.0 / questions.length) + "%",
                  "details", results
          ));
      }

      /**
       * 从LLM返回中提取JSON（兼容markdown代码块包裹的情况）
       */
      private String extractJson(String raw) {
          // 去掉 ```json ... ``` 包裹
          if (raw.contains("```")) {
              raw = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
          }
          // 提取第一个 { 到最后一个 }
          int start = raw.indexOf('{');
          int end = raw.lastIndexOf('}');
          if (start >= 0 && end > start) {
              return raw.substring(start, end + 1);
          }
          return raw;
      }
  }