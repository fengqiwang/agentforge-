package com.agentforge.report.sql.impl;

import com.agentforge.common.model.SqlGenerationResult;
import com.agentforge.common.model.TimeRange;
import com.agentforge.common.model.ValidationResult;
import com.agentforge.common.model.log.IAgentLogService;
import com.agentforge.framework.prompt.PromptManager;
import com.agentforge.framework.rag.FewShotRetriever;
import com.agentforge.framework.rag.SchemaRetriever;
import com.agentforge.framework.rag.TimeRangeParser;
import com.agentforge.report.sql.ISqlGeneratorService;
import com.agentforge.report.sql.template.SqlTemplateMatcher;
import com.agentforge.report.sql.template.TemplateMatchResult;
import com.agentforge.report.sql.ContextBuilder;
import com.agentforge.safety.sql.SqlSafetyValidator;
import com.agentforge.safety.sql.SqlSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqlGeneratorServiceImpl implements ISqlGeneratorService {

    private final SqlTemplateMatcher templateMatcher;
    private final FewShotRetriever fewShotRetriever;
    private final SchemaRetriever schemaRetriever;
    private final TimeRangeParser timeRangeParser;
    private final ChatModel chatModel;
    private final SqlSafetyValidator safetyValidator;
    private final SqlSanitizer sanitizer;
    private final PromptManager promptManager;
    private final ObjectMapper objectMapper;
    private final ContextBuilder contextBuilder;
    private final IAgentLogService agentLogService;
    private final com.agentforge.framework.memory.IChatMemoryManager chatMemoryManager;

    /**
     * 带会话上下文的 SQL 生成（支持多轮对话）
     * 作用：检测追问 → 注入上下文 → 生成 SQL
     *
     * @param question 用户问题
     * @param sessionId 会话ID（可为 null，null 时退化为单轮）
     */
    @Override
    public SqlGenerationResult generate(String question, String sessionId) {
        long start = System.currentTimeMillis();
        try {
            String enhancedQuestion = question;
            if (sessionId != null && !sessionId.isBlank()) {
                // 1. 注入结构化上下文（追问检测 + 历史摘要）
                // 如果 question 已经包含 [上下文信息]（由 SchemaAgent 注入），跳过重复构建
                String context = "";
                if (!question.contains("[上下文信息]")) {
                    context = contextBuilder.buildContext(sessionId, question);
                }

                // 2. 注入 ChatMemory 原始对话历史（LangChain4j 窗口中的最近消息）
                // 对话历史与结构化上下文互补，需单独注入
                String chatHistory = "";
                if (!question.contains("[对话历史]")) {
                    chatHistory = buildConversationHistory(sessionId);
                }

                if (!context.isBlank() || !chatHistory.isBlank()) {
                    StringBuilder enhanced = new StringBuilder(question);
                    if (!context.isBlank()) {
                        enhanced.append("\n\n[上下文信息]").append(context);
                    }
                    if (!chatHistory.isBlank()) {
                        enhanced.append("\n\n[对话历史]").append(chatHistory);
                    }
                    enhancedQuestion = enhanced.toString();
                    log.info("多轮对话增强：原始问题长度={}, 增强后={}, 含上下文={}, 含历史={}",
                            question.length(), enhancedQuestion.length(),
                            !context.isBlank(), !chatHistory.isBlank());
                }
            }
            SqlGenerationResult result = generate(enhancedQuestion);
            // 记录成功日志
            agentLogService.logSuccess(
                    sessionId,
                    "SqlGenerator",
                    question,
                    result.getSql(),
                    System.currentTimeMillis() - start,
                    result.getTokenCount()
            );

            return result;
        }catch (Exception e) {
            // 记录失败日志
            agentLogService.logFailure(
                    sessionId,
                    "SqlGenerator",
                    question,
                    System.currentTimeMillis() - start,
                    e.getMessage()
            );
            throw e;
        }
    }
    // ==================== 主入口 ====================

    @Override
    public SqlGenerationResult generate(String question) {
        log.info("========== SQL生成开始 ==========");
        log.info("问题：{}", question);

        // 解析时间范围（局部变量，线程安全）
        TimeRange timeRange = timeRangeParser.parse(question);
        log.info("时间范围：{} ~ {}（{}）", timeRange.getStart(), timeRange.getEnd(),
                timeRange.getExpression());

        SqlGenerationResult result;

        // Level 1: 模板匹配
        SqlGenerationResult templateResult = tryTemplateMatch(question, timeRange);
        if (templateResult.isMatched()) {
            result = templateResult;
            log.info("→ Level 1 模板命中");
        } else {
            // Level 2: Few-Shot RAG
            SqlGenerationResult fewShotResult = tryFewShotGeneration(question, timeRange);
            if (fewShotResult.isMatched()) {
                result = fewShotResult;
                //模板命中则不消耗token

                log.info("→ Level 2 Few-Shot命中");
            } else {
                // Level 3: LLM自由生成
                result = tryLlmGeneration(question, timeRange);
                log.info("→ Level 3 LLM生成");
            }
        }

        // 安全校验 + 清洗
        if (result.isMatched() && result.getSql() != null && !result.getSql().isBlank()) {
            result = validateAndSanitize(result);
        }

        log.info("========== SQL生成完成：level={}, sql={} ==========", result.getLevel(), result.getSql());
        return result;
    }

    // ==================== Level 1: 模板匹配 ====================

    private SqlGenerationResult tryTemplateMatch(String question, TimeRange timeRange) {
        TemplateMatchResult match = templateMatcher.match(question);
        if (match==null || !match.isMatched()) {
            return SqlGenerationResult.unmatched();
        }

        String sql = fillTemplateParams(match.getSql(), timeRange, question);

        // ===== 关键修正：检查残留占位符 =====
        if (hasUnresolvedPlaceholders(sql)) {
            log.info("模板SQL存在未填充占位符，放弃模板匹配，降级到Few-Shot/LLM");
            return SqlGenerationResult.unmatched();
        }

        return SqlGenerationResult.builder()
                .matched(true)
                .level("TEMPLATE")
                .sql(sql)
                .confidence(1.0)
                .explanation("模板匹配：" + match.getTemplateName())
                .tablesUsed(String.valueOf(match.getTablesUsed()))
                .build();
    }

    /**
     * 检查SQL中是否还有未填充的 {xxx} 占位符
     */
    private boolean hasUnresolvedPlaceholders(String sql) {
        if (sql == null) return false;
        return sql.indexOf('{') >= 0 && sql.indexOf('}') > sql.indexOf('{');
    }

    // ==================== Level 2: Few-Shot RAG ====================

    private SqlGenerationResult tryFewShotGeneration(String question, TimeRange timeRange) {
        List<FewShotRetriever.FewShotMatch> examples = fewShotRetriever.retrieve(question, 5);

        if (examples.isEmpty()) {
            log.info("Few-Shot未检索到相似示例");
            return SqlGenerationResult.unmatched();
        }

        FewShotRetriever.FewShotMatch bestMatch = examples.get(0);
        log.info("Few-Shot最佳匹配：score={}, question={}", bestMatch.getScore(), bestMatch.getQuestion());

        // 高相似度(≥0.85)：直接参考最佳示例
        if (bestMatch.getScore() >= 0.85) {
            String sql = fillTemplateParams(bestMatch.getSql(), timeRange, question);
            return SqlGenerationResult.builder()
                    .matched(true)
                    .level("FEW_SHOT")
                    .sql(sql)
                    .confidence(bestMatch.getScore())
                    .explanation("参考相似问题：" + bestMatch.getQuestion())
                    .tablesUsed(bestMatch.getTablesUsed())
                    .build();
        }

        // 中等相似度：Few-Shot上下文 + LLM生成
        return generateWithFewShotContext(question, timeRange, examples);
    }

    private SqlGenerationResult generateWithFewShotContext(
            String question, TimeRange timeRange, List<FewShotRetriever.FewShotMatch> examples) {

        String fewShotText = examples.stream()
                .map(e -> String.format("问题：%s\nSQL：%s", e.getQuestion(), e.getSql()))
                .collect(Collectors.joining("\n\n"));

        String schemaInfo = getRelatedSchema(question);
        String prompt = buildLlmPrompt(question, schemaInfo, timeRange, fewShotText);

        ChatResponse llmResponse = callLlm(prompt);
        return parseLlmResponse(llmResponse, "FEW_SHOT", timeRange);
    }

    // ==================== Level 3: LLM自由生成 ====================

    private SqlGenerationResult tryLlmGeneration(String question, TimeRange timeRange) {
        String schemaInfo = getRelatedSchema(question);
        String prompt = buildLlmPrompt(question, schemaInfo, timeRange, null);

        ChatResponse llmResponse = callLlm(prompt);
        return parseLlmResponse(llmResponse, "LLM", timeRange);
    }

    // ==================== 安全校验 + 清洗 ====================

    private SqlGenerationResult validateAndSanitize(SqlGenerationResult result) {
        String sql = result.getSql();

        // 1. 安全校验
        ValidationResult validation = safetyValidator.validate(sql);
        if (!validation.isPassed()) {
            log.warn("SQL安全校验失败：{}", validation.getErrorMessage());
            return SqlGenerationResult.builder()
                    .matched(true)
                    .level(result.getLevel())
                    .sql(sql)
                    .confidence(result.getConfidence())
                    .explanation("安全校验未通过：" + validation.getErrorMessage())
                    .tablesUsed(result.getTablesUsed())
                    .tokenCount(result.getTokenCount())
                    .build();
        }

        // 2. 清洗（补LIMIT、去注释等）
        String sanitized = sanitizer.sanitize(sql);

        return SqlGenerationResult.builder()
                .matched(true)
                .level(result.getLevel())
                .sql(sanitized)
                .confidence(result.getConfidence())
                .explanation(result.getExplanation())
                .tablesUsed(result.getTablesUsed())
                .tokenCount(result.getTokenCount())
                .build();
    }

    // ==================== 公共方法 ====================

    private String getRelatedSchema(String question) {
        try {
            return schemaRetriever.retrieve(question).toString();
        } catch (Exception e) {
            log.warn("Schema检索失败：{}", e.getMessage());
            return "Schema信息不可用";
        }
    }

    private String buildLlmPrompt(String question, String schemaInfo,
                                   TimeRange timeRange, String fewShotText) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("question", question);
        variables.put("schema", schemaInfo);
        variables.put("start_date", String.valueOf(timeRange.getStart()));
        variables.put("end_date", String.valueOf(timeRange.getEnd()));

        String basePrompt = promptManager.buildPrompt("sql-generation", variables);

        StringBuilder fullPrompt = new StringBuilder(basePrompt);
        if (fewShotText != null && !fewShotText.isEmpty()) {
            fullPrompt.append("\n\n## 相似示例（优先参考）\n").append(fewShotText);
        }
        fullPrompt.append("\n\n## 当前问题\n").append(question);

        return fullPrompt.toString();
    }

    private ChatResponse callLlm(String prompt) {
        List<ChatMessage> messages = List.of(UserMessage.from(prompt));
        return chatModel.chat(ChatRequest.builder().messages(messages).build());
    }

    // ==================== LLM响应解析（多策略） ====================

    private SqlGenerationResult parseLlmResponse(ChatResponse response, String level, TimeRange timeRange) {
        String responseText = response.aiMessage().text();
        TokenUsage usage = response.tokenUsage();
        int tokenCount = (usage != null)
                ? usage.inputTokenCount() + usage.outputTokenCount() : 0;
        if (responseText == null || responseText.isBlank()) {
            return SqlGenerationResult.builder()
                    .matched(true).level(level).sql("").confidence(0.1)
                    .explanation("LLM返回为空").build();
        }

        String sql = null;
        String explanation = null;
        String tablesUsed = null;

        // 策略1：从JSON提取（Jackson解析，可靠处理转义）
        try {
            String json = responseText.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            JsonNode node = objectMapper.readTree(json);
            if (node.has("sql")) sql = node.get("sql").asText();
            if (node.has("explanation")) explanation = node.get("explanation").asText();
            if (node.has("tables_used")) tablesUsed = node.get("tables_used").asText();
        } catch (Exception e) {
            log.debug("JSON解析失败，尝试其他策略");
        }

        // 策略2：从markdown SQL代码块提取
        if (sql == null || sql.isBlank()) {
            Pattern sqlBlock = Pattern.compile("```(?:sql)?\\s*\\n?(SELECT[\\s\\S]*?)\\n?```",
                    Pattern.CASE_INSENSITIVE);
            Matcher m = sqlBlock.matcher(responseText);
            if (m.find()) sql = m.group(1).trim();
        }

        // 策略3：直接找第一个SELECT语句
        if (sql == null || sql.isBlank()) {
            Pattern selectPattern = Pattern.compile("(SELECT\\s[\\s\\S]+?)(?:;|$)",
                    Pattern.CASE_INSENSITIVE);
            Matcher m = selectPattern.matcher(responseText);
            if (m.find()) sql = m.group(1).trim();
        }

        if (sql == null || sql.isBlank()) {
            log.warn("无法从LLM响应中提取SQL：{}", responseText.substring(0, Math.min(200, responseText.length())));
            return SqlGenerationResult.builder()
                    .matched(true).level(level).sql("").confidence(0.1)
                    .explanation("无法提取SQL").build();
        }

        // 去掉末尾分号
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);

        // 填充时间参数
        sql = fillTimeParams(sql, timeRange);

        return SqlGenerationResult.builder()
                .matched(true)
                .level(level)
                .sql(sql)
                .confidence("LLM".equals(level) ? 0.5 : 0.75)
                .explanation(explanation)
                .tablesUsed(tablesUsed)
                .tokenCount(tokenCount)
                .build();
    }

    // ==================== 时间参数填充（线程安全） ====================

    private String fillTimeParams(String sql, TimeRange timeRange) {
        if (timeRange == null || sql == null) return sql;

        // int类型参数（settledate）
        sql = sql.replace("{start_date}", String.valueOf(timeRange.getStart()))
                 .replace("{end_date}", String.valueOf(timeRange.getEnd()))
                 .replace("{today}", String.valueOf(timeRange.getStart()));

        // varchar类型参数（createtime等）
        if (timeRange.getStartStr() != null) {
            sql = sql.replace("{start_date_str}", timeRange.getStartStr())
                     .replace("{end_date_str}", timeRange.getEndStr());
        }

        return sql;
    }

    // ==================== 模板参数填充（含实体提取） ====================

    /**
     * 填充模板中的所有占位符（时间参数 + 实体参数如部门/城市名）
     */
    private String fillTemplateParams(String sql, TimeRange timeRange, String question) {
        sql = fillTimeParams(sql, timeRange);

        // 填充部门/城市关键词占位符
        if (sql.contains("{dept_keyword}")) {
            String keyword = extractLocationKeyword(question);
            sql = sql.replace("{dept_keyword}", keyword != null ? keyword : "%");
            log.info("提取归属关键词：question='{}', keyword='{}'", question, keyword);
        }

        return sql;
    }

    /**
     * 从用户问题中提取归属目标（城市名/部门名/分公司名）
     * 匹配模式："归属为XX"、"归属于XX"、"归属XX"、"归属为XX市/分公司/分部"
     */
    private String extractLocationKeyword(String question) {
        if (question == null || question.isBlank()) return null;

        // 匹配 "归属为/归属于/归属" 后面跟着的内容（非贪婪，优先匹配完整后缀）
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "归属(?:为|于)?\\s*([\\u4e00-\\u9fa5]{2,10}?(?:市|省|区|县|分公司|分部|营业部|支行)?)");
        java.util.regex.Matcher m = p.matcher(question);
        if (m.find()) {
            String keyword = m.group(1);
            // 排除纯虚词/停用词匹配
            if (keyword.length() >= 2) {
                return keyword;
            }
        }
        return null;
    }

    // ==================== 对话历史构建 ====================

    /**
     * 从 ChatMemoryManager 获取最近N轮对话历史，构建为 LLM prompt 上下文
     */
    private String buildConversationHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "";

        try {
            // 使用内存中的即时数据（MySQL 是异步写入，可能未就绪）
            var history = chatMemoryManager.getRecentMemory(sessionId);
            if (history == null || history.size() < 2) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("以下是该会话最近的对话记录，请结合上下文理解用户当前问题的意图：\n");

            int shown = 0;
            int maxShown = 6; // 最多显示6条（3轮对话）
            for (int i = Math.max(0, history.size() - maxShown); i < history.size(); i++) {
                var msg = history.get(i);
                String role = msg.getRole();
                String content = msg.getContent();
                if (content == null || content.isBlank()) continue;

                if ("user".equals(role)) {
                    sb.append("用户：").append(content).append("\n");
                    shown++;
                } else if ("assistant".equals(role)) {
                    // 截断 AI 回复避免 token 过长
                    if (content.length() > 300) {
                        sb.append("AI：").append(content, 0, 300).append("...\n");
                    } else {
                        sb.append("AI：").append(content).append("\n");
                    }
                    shown++;
                }
            }

            if (shown == 0) return "";
            log.info("构建对话历史上下文：sessionId={}, 显示{}条消息", sessionId, shown);
            return sb.toString();
        } catch (Exception e) {
            log.warn("构建对话历史失败：{}", e.getMessage());
            return "";
        }
    }
}
