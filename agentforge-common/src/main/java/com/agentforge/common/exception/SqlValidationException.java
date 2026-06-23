package com.agentforge.common.exception;

public class SqlValidationException extends AgentForgeException {
    public SqlValidationException(String message) {
        super("SQL_VALIDATION_FAILED", message);
    }
}
