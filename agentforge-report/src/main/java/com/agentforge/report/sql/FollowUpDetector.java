package com.agentforge.report.sql;

  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.util.List;
  import java.util.Map;
  import java.util.regex.Pattern;

  /**
   * 追问意图检测器
   * 作用：判断当前问题是否是追问，以及追问类型
   *
   * 追问类型：
   * - ADD_GROUP：追加分组，如"按商户类型分组呢"
   * - ADD_FILTER：追加过滤，如"只看收银宝的"
   * - ADD_ORDER：追加排序，如"按金额从高到低"
   * - CHANGE_TIME：修改时间范围，如"换成本月的"
   * - GENERAL：一般追问（无法精确分类）
   */
  @Slf4j
  @Component
  public class FollowUpDetector {

      public enum FollowUpType {
          NEW,          // 全新问题
          ADD_GROUP,    // 追加分组
          ADD_FILTER,   // 追加过滤条件
          ADD_ORDER,    // 追加排序
          CHANGE_TIME,  // 修改时间范围
          GENERAL       // 一般追问
      }

      // 关键词规则（按优先级匹配）
      private static final List<Map.Entry<Pattern, FollowUpType>> RULES = List.of(
              Map.entry(Pattern.compile("按.+分[组类]"), FollowUpType.ADD_GROUP),
              Map.entry(Pattern.compile("分[组类]看|分[组类]呢"), FollowUpType.ADD_GROUP),
              Map.entry(Pattern.compile("只看|只要|排除|不要|去掉|不包括"), FollowUpType.ADD_FILTER),
              Map.entry(Pattern.compile("排序|从高到低|从低到高|降序|升序|最多|最少|前\\d+"), FollowUpType.ADD_ORDER),
              Map.entry(Pattern.compile("换成|改为|本月|上周|上月|昨天|最近\\d+天|今年|去年"), FollowUpType.CHANGE_TIME),
              Map.entry(Pattern.compile("呢$|吗$|吧$|怎么样|如何|什么"), FollowUpType.GENERAL),
              Map.entry(Pattern.compile("那.*呢|那.*怎么|还有|另外|再"), FollowUpType.GENERAL)
      );

      /**
       * 检测追问类型
       * @param question 当前问题
       * @param previousQuestion 上一轮问题（可为 null）
       * @return 追问类型
       */
      public FollowUpType detect(String question, String previousQuestion) {
          if (previousQuestion == null || previousQuestion.isBlank()) {
              return FollowUpType.NEW;
          }

          // 短问题 + 包含追问关键词 → 大概率是追问（放宽阈值 30→50）
          if (question.length() < 50 || question.contains("呢")
                  || question.contains("那") || question.contains("换成")
                  || question.contains("还有") || question.contains("另外")) {

              for (Map.Entry<Pattern, FollowUpType> rule : RULES) {
                  if (rule.getKey().matcher(question).find()) {
                      log.info("检测到追问：type={}, question={}", rule.getValue(), question);
                      return rule.getValue();
                  }
              }
          }

          // 默认：问题太短可能是追问，长问题视为新问题
          if (question.length() < 20) {
              return FollowUpType.GENERAL;
          }

          return FollowUpType.NEW;
      }
  }