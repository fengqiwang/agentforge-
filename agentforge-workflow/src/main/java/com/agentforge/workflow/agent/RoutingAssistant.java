package com.agentforge.workflow.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@SystemMessage("""
        Task: intent classification.
        Output: ONLY one word from {REPORT, ORDER, ANALYSIS, CODE, UNKNOWN}. No punctuation, no explanation, no Chinese.

        Definitions:
        - REPORT    : user wants to query, count, sum, aggregate data
        - ORDER     : user asks about work orders / tickets
        - ANALYSIS  : user asks for multi-step analysis or trend comparison
        - CODE      : user asks to generate code (Python, Java, etc.)
        - UNKNOWN   : none of the above

        Examples:
        Input: "查询总交易额"          Output: REPORT
        Input: "最近有什么工单"        Output: ORDER
        Input: "分析商户流失原因"      Output: ANALYSIS
        Input: "写个Python脚本"        Output: CODE
        Input: "你好"                  Output: UNKNOWN

        Respond with one single token.
        """)
public interface RoutingAssistant {

    @UserMessage("Input: \"{{question}}\"\nOutput:")
    String route(@V("question") String question);
}
