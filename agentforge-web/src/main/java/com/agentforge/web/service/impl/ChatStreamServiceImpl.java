package com.agentforge.web.service.impl;

import com.agentforge.framework.memory.IChatMemoryManager;
import com.agentforge.web.service.IChatStreamService;
import com.agentforge.workflow.pipeline.AgentContext;
import com.agentforge.workflow.pipeline.AgentPipeline;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 聊天流式响应编排服务 — ChatController 的业务逻辑层。
 */
@Slf4j
@Service
public class ChatStreamServiceImpl implements IChatStreamService {

    private final AgentPipeline agentPipeline;
    private final IChatMemoryManager chatMemoryManager;

    private final ExecutorService pipelineExecutor;
    private final ScheduledExecutorService scheduler;

    public ChatStreamServiceImpl(AgentPipeline agentPipeline, IChatMemoryManager chatMemoryManager) {
        this.agentPipeline = agentPipeline;
        this.chatMemoryManager = chatMemoryManager;
        this.pipelineExecutor = Executors.newFixedThreadPool(
                Math.max(8, Runtime.getRuntime().availableProcessors() * 2),
                r -> {
                    Thread t = new Thread(r, "agent-pipeline");
                    t.setDaemon(true);
                    return t;
                });
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agent-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    public void shutdown() {
        pipelineExecutor.shutdown();
        scheduler.shutdown();
        try {
            if (!pipelineExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                pipelineExecutor.shutdownNow();
            }
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 在后台线程中编排 Agent 管道 → 构建 SSE 事件。 */
    @Override
    public void executeAndStream(SseEmitter emitter, String sessionId, String question, String enhancedQuestion) {
        CompletableFuture.runAsync(() -> {
            ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(
                    () -> sendHeartbeat(emitter), 15, 15, TimeUnit.SECONDS);

            try {
                emitter.send(SseEmitter.event()
                        .data(Map.of("type", "status", "message", "正在分析问题...")));

                AgentContext ctx = new AgentContext();
                ctx.setSessionId(sessionId);
                ctx.setUserQuestion(question);
                ctx.setEnhancedQuestion(enhancedQuestion);
                agentPipeline.execute(ctx);

                persistResponse(sessionId, ctx);
                sendResultEvents(emitter, ctx);

                emitter.send(SseEmitter.event().data(Map.of("type", "done")));
                emitter.complete();
            } catch (Exception e) {
                heartbeat.cancel(false);
                log.error("流式SQL生成失败：{}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event()
                            .data(Map.of("type", "error", "message", e.getMessage())));
                } catch (IOException ex) {
                    log.debug("SSE 错误通知发送失败（客户端可能已断开）: {}", ex.getMessage());
                }
                emitter.completeWithError(e);
            }
        }, pipelineExecutor);
    }

    private void sendHeartbeat(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("hb"));
        } catch (IOException e) {
            log.debug("SSE 心跳发送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    private void persistResponse(String sessionId, AgentContext ctx) {
        try {
            String aiText;
            String meta = ctx.getGeneratedSql();
            if (ctx.getErrorMessage() != null && ctx.getFormattedResponse() == null) {
                aiText = "处理失败：" + ctx.getErrorMessage();
                meta = null;
            } else if (ctx.getSqlExplanation() != null && !ctx.getSqlExplanation().isBlank()) {
                aiText = ctx.getSqlExplanation();
            } else if (ctx.getGeneratedSql() != null) {
                aiText = "已生成并执行 SQL，详情见报表";
            } else if (ctx.getFormattedResponse() != null) {
                aiText = "已生成报表";
            } else {
                aiText = "处理完成";
            }
            chatMemoryManager.addAssistantMessage(sessionId, aiText, meta, null);
        } catch (Exception e) {
            log.warn("AI 回复持久化失败（不影响响应）: {}", e.getMessage());
        }
    }

    private void sendResultEvents(SseEmitter emitter, AgentContext ctx) throws IOException {
        if (ctx.getErrorMessage() != null && ctx.getFormattedResponse() == null) {
            emitter.send(SseEmitter.event().data(Map.of(
                    "type", "error",
                    "message", ctx.getErrorMessage()
            )));
            return;
        }
        if (ctx.getGeneratedSql() != null) {
            emitter.send(SseEmitter.event().data(Map.of(
                    "type", "sql_result",
                    "level", ctx.getSqlLevel() != null ? ctx.getSqlLevel() : "",
                    "sql", ctx.getGeneratedSql(),
                    "explanation", ctx.getSqlExplanation() != null ? ctx.getSqlExplanation() : "",
                    "confidence", ctx.getSqlConfidence() != null ? ctx.getSqlConfidence().toString() : "0"
            )));
        }
        if (ctx.getFormattedResponse() != null) {
            emitter.send(SseEmitter.event().data(Map.of(
                    "type", "report",
                    "data", ctx.getFormattedResponse()
            )));
        }
        if (ctx.getReviewNote() != null) {
            emitter.send(SseEmitter.event().data(Map.of(
                    "type", "review",
                    "note", ctx.getReviewNote()
            )));
        }
    }
}
