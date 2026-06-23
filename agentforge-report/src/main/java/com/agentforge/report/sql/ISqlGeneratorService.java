package com.agentforge.report.sql;

import com.agentforge.common.model.SqlGenerationResult;

public interface ISqlGeneratorService {

    SqlGenerationResult generate(String question, String sessionId);

    SqlGenerationResult generate(String question);
}
