package com.agentforge.common.exception;

public class LlmCallException extends AgentForgeException {
    public LlmCallException(String message) {
        super("LLM_ERROR", message);
    }

    public LlmCallException(String message, Throwable cause) {
        super("LLM_ERROR", message, cause);
    }
}
