package com.agentforge.workflow.pipeline;

public interface Agent {
    String getName();
    void execute(AgentContext ctx);
}
