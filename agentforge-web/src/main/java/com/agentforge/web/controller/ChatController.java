package com.agentforge.web.controller;

import com.agentforge.framework.memory.IChatMemoryManager;
import com.agentforge.framework.memory.ISessionService;
import com.agentforge.report.sql.ContextBuilder;
import com.agentforge.web.service.IChatStreamService;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatModel chatModel;
    private final IChatMemoryManager chatMemoryManager;
    private final ISessionService sessionService;
    private final ContextBuilder contextBuilder;
    private final IChatStreamService chatStreamService;

    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return chatModel.chat(message);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sqlStream(@RequestParam String sessionId,
                                @RequestParam String question) {
        SseEmitter emitter = new SseEmitter(600_000L);

        chatMemoryManager.addUserMessage(sessionId, question);
        sessionService.touch(sessionId);

        String context = contextBuilder.buildContext(sessionId, question);
        String enhancedQuestion = context.isBlank()
                ? question
                : question + "\n\n[上下文信息]" + context;

        log.info("当前上下文信息：{}", enhancedQuestion);

        chatStreamService.executeAndStream(emitter, sessionId, question, enhancedQuestion);
        return emitter;
    }
}
