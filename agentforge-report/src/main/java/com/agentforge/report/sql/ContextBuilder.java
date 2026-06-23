  package com.agentforge.report.sql;

  import com.agentforge.framework.memory.IChatMemoryManager;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.util.ArrayList;
  import java.util.List;

  /**
   * 多轮上下文构建器
   * 作用：将历史对话摘要注入 Prompt，让 LLM 理解追问意图
   *
   * 构建策略：
   * - 全新问题：不注入上下文，直接生成
   * - 追问：将前一轮的问题和 SQL 作为上下文注入
   */
  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class ContextBuilder {

      private final IChatMemoryManager chatMemoryManager;
      private final FollowUpDetector followUpDetector;

      /**
       * 构建上下文描述（注入到 Prompt 中）
       * @param sessionId 会话ID
       * @param currentQuestion 当前问题
       * @return 上下文描述字符串，可为空
       */
      public String buildContext(String sessionId, String currentQuestion) {
          // 使用内存中的即时数据（MySQL 是异步写入，可能未就绪）
          List<com.agentforge.common.model.conversation.ChatMessage> history =
                  chatMemoryManager.getRecentMemory(sessionId);

          if (history == null || history.size() < 2) {
              return "";
          }

          // 收集最近3轮对话（user + assistant 配对）
          int maxRounds = 3;
          List<String> recentUserMsgs = new ArrayList<>();
          List<String> recentAssistantMsgs = new ArrayList<>();
          for (int i = history.size() - 1; i >= 0 && recentUserMsgs.size() < maxRounds; i--) {
              com.agentforge.common.model.conversation.ChatMessage msg = history.get(i);
              if ("assistant".equals(msg.getRole()) && recentAssistantMsgs.size() < maxRounds) {
                  recentAssistantMsgs.add(0, msg.getContent());
              }
              if ("user".equals(msg.getRole()) && recentUserMsgs.size() < maxRounds) {
                  recentUserMsgs.add(0, msg.getContent());
              }
          }

          if (recentUserMsgs.isEmpty()) return "";

          String lastUserMsg = recentUserMsgs.get(recentUserMsgs.size() - 1);

          // 检测追问类型
          FollowUpDetector.FollowUpType followUpType =
                  followUpDetector.detect(currentQuestion, lastUserMsg);

          if (followUpType == FollowUpDetector.FollowUpType.NEW) {
              return "";
          }

          // 构建上下文（含最近3轮对话摘要）
          StringBuilder context = new StringBuilder();
          context.append("\n## 历史对话上下文（最近").append(recentUserMsgs.size()).append("轮）\n");

          for (int i = 0; i < recentUserMsgs.size(); i++) {
              context.append("第").append(i + 1).append("轮：\n");
              context.append("用户：").append(recentUserMsgs.get(i)).append("\n");
              if (i < recentAssistantMsgs.size()) {
                  String reply = recentAssistantMsgs.get(i);
                  context.append("AI回复摘要：");
                  if (reply != null && reply.length() > 800) {
                      context.append(reply, 0, 800).append("...");
                  } else {
                      context.append(reply != null ? reply : "(无)");
                  }
                  context.append("\n");
              }
          }

          context.append("追问类型：").append(followUpType.name()).append("\n");
          context.append("当前问题：").append(currentQuestion).append("\n");
          context.append("请结合上下文理解用户意图。\n");

          log.info("构建追问上下文：type={}, 历史轮数={}",
                  followUpType, recentUserMsgs.size());

          return context.toString();
      }
  }