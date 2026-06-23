package com.agentforge.report;

import com.agentforge.common.exception.LlmCallException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReportAgent {

    private final ReportAssistant assistant;

    public ReportAgent(ChatModel chatModel) {
        this.assistant = AiServices.builder(ReportAssistant.class)
                .chatModel(chatModel)
                .build();
    }

    public String generateReport(String sessionId, String userQuestion, String queryResult) {
        log.info("Generating report for session: {}", sessionId);
        try {
            return assistant.generateReport(sessionId, userQuestion, queryResult);
        } catch (Exception e) {
            log.error("Report generation failed for session: {}", sessionId, e);
            throw new LlmCallException("Report generation failed: " + e.getMessage(), e);
        }
    }
}
