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
  public class ChatSession {

      private Long id;
      private String sessionId;
      private Long userId;
      private String title;
      /** 1=活跃 2=归档 */
      private Integer status;
      private LocalDateTime createdAt;
      private LocalDateTime updatedAt;
  }