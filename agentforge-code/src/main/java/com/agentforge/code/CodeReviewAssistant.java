package com.agentforge.code;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@SystemMessage("""
你是专业的代码审查者。分析所提供的代码变更，并进行结构化审查，涵盖：
    1. 潜在的错误或逻辑错误
    2.安全漏洞
    3.性能问题
    4.代码风格与最佳实践
    5.改进建议 除非用户另有说明，请使用中文（简体）回复。
""")
public interface CodeReviewAssistant {

    @UserMessage("Review pull request #{{prNumber}} from repository {{repoUrl}}. Here are the changes:\n{{diff}}")
    String review(@V("repoUrl") String repoUrl,
                  @V("prNumber") int prNumber,
                  @V("diff") String diff);
}
