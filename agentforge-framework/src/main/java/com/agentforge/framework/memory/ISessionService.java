package com.agentforge.framework.memory;

import com.agentforge.common.model.conversation.ChatSession;

import java.util.List;

public interface ISessionService {

    /**
     * 创建新会话
     */
    ChatSession create(Long userId, String title);

    /**
     * 查询用户的所有会话
     */
    List<ChatSession> listByUser(Long userId);

    /**
     * 获取会话详情
     */
    ChatSession getBySessionId(String sessionId);

    /**
     * 更新会话标题（首次对话后用 LLM 生成摘要标题）
     */
    void updateTitle(String sessionId, String title);

    /**
     * 更新会话活跃时间
     */
    void touch(String sessionId);

    /**
     * 归档会话（逻辑删除）
     */
    void archive(String sessionId);

    /**
     * 删除会话（物理删除）
     */
    void delete(String sessionId);
}
