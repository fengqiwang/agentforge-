package com.agentforge.web.exception;

import com.agentforge.common.exception.*;
import com.agentforge.common.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(SqlValidationException.class)
    public ResponseEntity<ApiResponse> handleSqlValidationException(SqlValidationException e) {
        log.warn("SQL校验失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(PromptInjectionException.class)
    public ResponseEntity<ApiResponse> handlePromptInjection(PromptInjectionException e) {
        log.warn("Prompt注入检测: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(SqlExecutionTimeoutException.class)
    public ResponseEntity<ApiResponse> handleSqlTimeout(SqlExecutionTimeoutException e) {
        log.warn("SQL执行超时: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(LlmCallException.class)
    public ResponseEntity<ApiResponse> handleLlmError(LlmCallException e) {
        log.error("LLM调用失败", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(SchemaRetrievalException.class)
    public ResponseEntity<ApiResponse> handleSchemaError(SchemaRetrievalException e) {
        log.error("Schema检索失败", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(AgentForgeException.class)
    public ResponseEntity<ApiResponse> handleGeneric(AgentForgeException e) {
        log.error("业务异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleUnknown(Exception e) {
        log.error("未知异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "服务内部错误"));
    }
}
