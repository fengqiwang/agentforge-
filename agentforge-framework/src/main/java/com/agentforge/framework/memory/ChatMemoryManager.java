package com.agentforge.framework.memory;

import com.agentforge.common.model.conversation.ChatMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话记忆管理器
 * 作用：统一管理三级存储，对外提供简洁的 API
 *
 * 三级存储：
 * 1. InMemory（LangChain4j MessageWindowChatMemory）— LLM 调用直接使用
 * 2. Redis — 热会话缓存，应用重启后恢复
 * 3. MySQL — 持久化，支持无限量历史回查
 */
@Slf4j
@Service
public class ChatMemoryManager {

    private static final int WINDOW_SIZE = 20;

    private final RedisMessageStore redisMessageStore;
    private final MysqlMessageStore mysqlMessageStore;

    /** 每个会话一个 ChatMemory 实例，内存中缓存 */
    private final Map<String, ChatMemory> memoryCache = new ConcurrentHashMap<>();

    public ChatMemoryManager(RedisMessageStore redisMessageStore,
                             MysqlMessageStore mysqlMessageStore) {
        this.redisMessageStore = redisMessageStore;
        this.mysqlMessageStore = mysqlMessageStore;
    }

    /**
     * 获取或创建会话的 ChatMemory
     * 作用：先查内存 → 再查 Redis 恢复 → 返回 LangChain4j 可用的 ChatMemory
     */
    public ChatMemory getOrCreate(String sessionId) {
        return memoryCache.computeIfAbsent(sessionId, this::restoreFromRedis);
    }

    /**
     * 添加用户消息
     * 作用：三级存储同步写入
     */
    public void addUserMessage(String sessionId, String content) {
        ChatMemory memory = getOrCreate(sessionId);
        memory.add(UserMessage.from(content));

        ChatMessage msg = ChatMessage.builder()
                .sessionId(sessionId)
                .role("user")
                .content(content)
                .metadata("添加用户消息")
                .tokenCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        redisMessageStore.append(sessionId, msg);
        mysqlMessageStore.saveAsync(msg);
    }

    /**
     * 添加 AI 回复
     * 作用：三级存储同步写入，可附带 metadata（如 SQL、耗时）
     */
    public void addAssistantMessage(String sessionId, String content,
                                    String metadata, Integer tokenCount) {
        ChatMemory memory = getOrCreate(sessionId);
        memory.add(AiMessage.from(content));

        ChatMessage msg = ChatMessage.builder()
                .sessionId(sessionId)
                .role("assistant")
                .content(content)
                .metadata(metadata)
                .tokenCount(tokenCount)
                .createdAt(LocalDateTime.now())
                .build();

        redisMessageStore.append(sessionId, msg);
        mysqlMessageStore.saveAsync(msg);
    }

    /**
     * 设置系统提示
     */
    public void setSystemMessage(String sessionId, String systemPrompt) {
        ChatMemory memory = getOrCreate(sessionId);
        memory.add(SystemMessage.from(systemPrompt));
    }

    /**
     * 获取会话的 LangChain4j 消息列表（供 LLM 调用使用）
     */
    public List<dev.langchain4j.data.message.ChatMessage> getMessages(String sessionId) {
        ChatMemory memory = getOrCreate(sessionId);
        return memory.messages();
    }

    /**
     * 获取完整历史消息（从 MySQL）
     * 作用：前端展示历史对话时使用
     */
    public List<ChatMessage> getFullHistory(String sessionId) {
        return mysqlMessageStore.getMessages(sessionId);
    }

    /**
     * 获取最近对话上下文（内存优先 → Redis恢复 → MySQL兜底）
     * 作用：生成 SQL 时注入上下文，兼顾即时性和持久化
     * 策略：内存有则直接用 → 内存空则从 Redis 恢复 → Redis 也无则从 MySQL 回查
     * @return user/assistant 交替的最近消息列表
     */
    public List<ChatMessage> getRecentMemory(String sessionId) {
        // 第一层：内存（最快，包含当前请求刚写入的消息）
        ChatMemory memory = memoryCache.get(sessionId);
        if (memory == null) {
            // 第二层：从 Redis 恢复到内存
            memory = restoreFromRedis(sessionId);
            if (memory != null && !memory.messages().isEmpty()) {
                memoryCache.put(sessionId, memory);
            }
        }
        if (memory == null || memory.messages().isEmpty()) {
            // 第三层：Redis 也过期了，从 MySQL 回查并恢复到内存
            log.info("内存/Redis均无会话数据，从MySQL回查: sessionId={}", sessionId);
            List<ChatMessage> fromDb = mysqlMessageStore.getMessages(sessionId);
            if (fromDb.isEmpty()) return List.of();

            // 恢复到内存供后续使用
            memory = MessageWindowChatMemory.builder().maxMessages(WINDOW_SIZE).build();
            for (ChatMessage msg : fromDb) {
                switch (msg.getRole()) {
                    case "user" -> memory.add(UserMessage.from(msg.getContent()));
                    case "assistant" -> memory.add(AiMessage.from(msg.getContent()));
                    case "system" -> memory.add(SystemMessage.from(msg.getContent()));
                }
            }
            memoryCache.put(sessionId, memory);
            return fromDb;
        }

        // 内存/Redis 命中：转换 LangChain4j 类型为自定义 ChatMessage
        List<ChatMessage> result = new java.util.ArrayList<>();
        for (dev.langchain4j.data.message.ChatMessage m : memory.messages()) {
            String role;
            String content;
            if (m instanceof UserMessage um) {
                role = "user";
                content = um.singleText();
            } else if (m instanceof AiMessage am) {
                role = "assistant";
                content = am.text();
            } else {
                continue;
            }
            result.add(ChatMessage.builder()
                    .sessionId(sessionId)
                    .role(role)
                    .content(content)
                    .build());
        }
        return result;
    }

    /**
     * 清除会话记忆
     */
    public void clear(String sessionId) {
        memoryCache.remove(sessionId);
        redisMessageStore.delete(sessionId);
        mysqlMessageStore.deleteBySessionId(sessionId);
    }

    /**
     * 会话数量（监控用）
     */
    public int activeSessionCount() {
        return memoryCache.size();
    }

    // ==================== 内部方法 ====================

    /**
     * 从 Redis 恢复会话到内存
     * 作用：应用重启后 InMemory 丢失，从 Redis 恢复最近 WINDOW_SIZE 条
     */
    private ChatMemory restoreFromRedis(String sessionId) {
        List<ChatMessage> cached = redisMessageStore.getRecentMessages(
                sessionId, WINDOW_SIZE);

        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(WINDOW_SIZE)
                .build();

        if (!cached.isEmpty()) {
            for (ChatMessage msg : cached) {
                switch (msg.getRole()) {
                    case "user" -> memory.add(UserMessage.from(msg.getContent()));
                    case "assistant" -> memory.add(AiMessage.from(msg.getContent()));
                    case "system" -> memory.add(SystemMessage.from(msg.getContent()));
                }
            }
            log.info("从Redis恢复会话：sessionId={}, 消息数={}", sessionId, cached.size());
        }

        return memory;
    }
}