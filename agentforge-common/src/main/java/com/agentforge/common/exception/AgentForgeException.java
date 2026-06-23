package com.agentforge.common.exception;

import lombok.Getter;

@Getter
public abstract class AgentForgeException extends RuntimeException {
    private final String code;

    protected AgentForgeException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected AgentForgeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    public String getCode() { return code; }
}
