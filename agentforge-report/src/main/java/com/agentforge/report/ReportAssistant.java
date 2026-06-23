package com.agentforge.report;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@SystemMessage("""
        You are a data analyst assistant. Given SQL query results and the user's
        original question, generate a clear, structured analysis report.
        Include summary tables, key findings, and actionable insights.
        Respond in Chinese (Simplified) unless the user specifies otherwise.
        """)
public interface ReportAssistant {

    @UserMessage("""
            Session: {{sessionId}}
            User question: {{userQuestion}}
            Query results: {{queryResult}}

            Please generate a comprehensive report answering the user's question
            based on the query results above.
            """)
    String generateReport(@V("sessionId") String sessionId,
                          @V("userQuestion") String userQuestion,
                          @V("queryResult") String queryResult);
}
