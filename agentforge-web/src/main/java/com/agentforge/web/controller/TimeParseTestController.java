  package com.agentforge.web.controller;

  import com.agentforge.framework.rag.TimeRangeParser;
  import lombok.RequiredArgsConstructor;
  import org.springframework.web.bind.annotation.*;

  import java.util.List;
  import java.util.Map;
  import java.util.stream.Collectors;

  @RestController
  @RequestMapping("/api/test/time")
  @RequiredArgsConstructor
  public class TimeParseTestController {

      private final TimeRangeParser timeRangeParser;

      @PostMapping("/parse")
      public Map<String, String> parse(@RequestBody Map<String, String> request) {
          return timeRangeParser.testParse(request.get("question"));
      }

      @GetMapping("/batch")
      public List<Map<String, String>> batchParse() {
          List<String> questions = List.of(
                  "今天的交易总额",
                  "昨天的交易笔数",
                  "近7天的交易额",
                  "上周的交易情况",
                  "上个月的交易总额",
                  "上上个月的交易笔数",
                  "3月份的交易额",
                  "去年12月的交易总额",
                  "今年的交易总额",
                  "2024年3月15日的交易明细"
          );
          return questions.stream()
                  .map(timeRangeParser::testParse)
                  .collect(Collectors.toList());
      }
  }