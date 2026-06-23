package com.agentforge.code;

import com.agentforge.common.exception.LlmCallException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CodeReviewAgent {

    private final CodeReviewAssistant assistant;

    public CodeReviewAgent(ChatModel chatModel) {
        this.assistant = AiServices.builder(CodeReviewAssistant.class)
                .chatModel(chatModel)
                .build();
    }

    public String review(String repoUrl, int prNumber, String diff) {
        log.info("Reviewing PR #{} from {}", prNumber, repoUrl);
        try {
            return assistant.review(repoUrl, prNumber, diff);
        } catch (Exception e) {
            log.error("Code review failed for PR #{}", prNumber, e);
            throw new LlmCallException("Code review failed: " + e.getMessage(), e);
        }
    }
}
