package com.agentforge.common.exception;

public class PromptInjectionException extends AgentForgeException{
    protected PromptInjectionException(String message) {
        super("PROMPT_INJECTION", message);
    }
}
