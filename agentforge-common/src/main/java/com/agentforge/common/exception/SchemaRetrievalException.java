package com.agentforge.common.exception;

public class SchemaRetrievalException extends AgentForgeException{
    protected SchemaRetrievalException(String message) {
        super("SCHEMA_ERROR", message);
    }

    protected SchemaRetrievalException(String message, Throwable cause) {
        super("SCHEMA_ERROR", message, cause);
    }
}
