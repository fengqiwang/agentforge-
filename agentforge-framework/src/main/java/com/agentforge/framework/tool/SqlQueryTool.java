package com.agentforge.framework.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SqlQueryTool {

    @Tool("Execute a SQL query and return results")
    public String executeQuery(String sql) {
        throw new UnsupportedOperationException("SqlQueryTool is reserved for LangChain4j Tool Calling (future feature)");
    }
}
