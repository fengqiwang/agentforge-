package com.agentforge.web.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 聊天流式响应编排服务接口。
 */
public interface IChatStreamService {

    /** 在后台线程中编排 Agent 管道 → 构建 SSE 事件。 */
    void executeAndStream(SseEmitter emitter, String sessionId, String question, String enhancedQuestion);
}
