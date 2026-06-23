package com.agentforge.framework.memory;

import com.agentforge.common.model.conversation.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.List;

public interface IChatMemoryManager {

    /**
     * 获取或创建会话的 ChatMemory
     * 作用：先查内存 → 再查 Redis 恢复 → 返回 LangChain4j 可用的 ChatMemory
     */
    ChatMemory getOrCreate(String sessionId);

    /**
     * 添加用户消息
     * 作用：三级存储同步写入
     */
    void addUserMessage(String sessionId, String content);

    /**
     * 添加 AI 回复
     * 作用：三级存储同步写入，可附带 metadata（如 SQL、耗时）
     */
    void addAssistantMessage(String sessionId, String content,
                             String metadata, Integer tokenCount);

    /**
     * 设置系统提示
     */
    void setSystemMessage(String sessionId, String systemPrompt);

    /**
     * 获取会话的 LangChain4j 消息列表（供 LLM 调用使用）
     */
    List<dev.langchain4j.data.message.ChatMessage> getMessages(String sessionId);

    /**
     * 获取完整历史消息（从 MySQL）
     * 作用：前端展示历史对话时使用
     */
    List<ChatMessage> getFullHistory(String sessionId);

    /**
     * 获取最近对话上下文（内存优先 → Redis恢复 → MySQL兜底）
     * 作用：生成 SQL 时注入上下文，兼顾即时性和持久化
     * 策略：内存有则直接用 → 内存空则从 Redis 恢复 → Redis 也无则从 MySQL 回查
     * @return user/assistant 交替的最近消息列表
     */
    List<ChatMessage> getRecentMemory(String sessionId);

    /**
     * 清除会话记忆
     */
    void clear(String sessionId);

    /**
     * 会话数量（监控用）
     */
    int activeSessionCount();
}
