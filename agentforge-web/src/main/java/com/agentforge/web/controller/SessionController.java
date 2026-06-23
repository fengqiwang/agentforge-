 package com.agentforge.web.controller;

  import com.agentforge.common.model.conversation.ChatMessage;
  import com.agentforge.common.model.conversation.ChatSession;
  import com.agentforge.framework.memory.IChatMemoryManager;
  import com.agentforge.framework.memory.ISessionService;
  import lombok.RequiredArgsConstructor;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.*;

  import java.util.List;
  import java.util.Map;

  @RestController
  @RequestMapping("/api/session")
  @RequiredArgsConstructor
  public class SessionController {

      private final ISessionService sessionService;
      private final IChatMemoryManager chatMemoryManager;

      /**
       * 创建会话
       * POST /api/session/create?userId=1&title=新对话
       * Body 可为空；也接受 JSON body { "title": "...", "userId": 1 } 兼容老前端
       */
      @PostMapping("/create")
      public ChatSession create(@RequestParam(value = "userId", defaultValue = "0") Long userId,
                                @RequestParam(value = "title", defaultValue = "新对话") String title,
                                @RequestBody(required = false) Map<String, Object> body) {
          if (body != null) {
              if (body.get("title") != null) title = String.valueOf(body.get("title"));
              if (body.get("userId") != null) userId = Long.parseLong(String.valueOf(body.get("userId")));
          }
          return sessionService.create(userId, title);
      }

      /**
       * 列出用户的所有会话
       * GET /api/session?userId=1
       */
      @GetMapping("/list")
      public List<ChatSession> list(@RequestParam(defaultValue = "0") Long userId) {
          return sessionService.listByUser(userId);
      }

      /**
       * 获取会话详情（含历史消息）
       * GET /api/session/{sessionId}
       */
      @GetMapping("/{sessionId}")
      public ResponseEntity<?> get(@PathVariable String sessionId) {
          ChatSession session = sessionService.getBySessionId(sessionId);
          if (session == null) {
              return ResponseEntity.status(HttpStatus.NOT_FOUND)
                      .body(Map.of("error", "会话不存在"));
          }
          List<ChatMessage> messages = chatMemoryManager.getFullHistory(sessionId);

          return ResponseEntity.ok(Map.of(
                  "session", session,
                  "messages", messages
          ));
      }

      /**
       * 删除会话
       * DELETE /api/session/{sessionId}
       */
      @DeleteMapping("/{sessionId}")
      public Map<String, Object> delete(@PathVariable String sessionId) {
          sessionService.delete(sessionId);
          return Map.of("success", true);
      }

      /**
       * 归档会话
       * PUT /api/session/{sessionId}/archive
       */
      @PutMapping("/{sessionId}/archive")
      public Map<String, Object> archive(@PathVariable String sessionId) {
          sessionService.archive(sessionId);
          return Map.of("success", true);
      }
  }