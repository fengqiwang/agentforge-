package com.agentforge.common.model.conversation;

  import lombok.AllArgsConstructor;
  import lombok.Builder;
  import lombok.Data;
  import lombok.NoArgsConstructor;

  import java.time.LocalDateTime;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public class ChatMessage {

      /** 消息ID */
      private Long id;

      /** 会话ID */
      private String sessionId;

      /** 角色：user / assistant / system */
      private String role;

      /** 内容 */
      private String content;

      /** 额外元数据（JSON格式，如执行的SQL、耗时等） */
      private String metadata;

      /** Token消耗数量 */
      private Integer tokenCount;

      /** 创建时间 */
      private LocalDateTime createdAt;
  }