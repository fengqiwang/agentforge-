package com.agentforge.common.exception;

public class SqlExecutionTimeoutException extends AgentForgeException {
    public SqlExecutionTimeoutException(String message) {
        super("SQL_TIMEOUT", message);
    }
}
