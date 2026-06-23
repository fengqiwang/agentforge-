package com.agentforge.workflow.agent;

import com.agentforge.workflow.pipeline.Agent;
import com.agentforge.workflow.pipeline.AgentContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 路由 Agent — 混合策略
 * 1. 关键词优先：快速、确定性，命中即返回
 * 2. LLM 兜底：关键词未命中时调 LLM 做意图分类
 */
@Slf4j
@Component
public class RouterAgent implements Agent {

    private static final Set<String> VALID_INTENTS =
            Set.of("REPORT", "ANALYSIS", "ORDER", "CODE", "UNKNOWN");
    private static final Pattern INTENT_PATTERN =
            Pattern.compile("\\b(REPORT|ANALYSIS|ORDER|CODE|UNKNOWN)\\b", Pattern.CASE_INSENSITIVE);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public RouterAgent(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() { return "router"; }

    @Override
    public void execute(AgentContext ctx) {
        String question = ctx.getUserQuestion();
        log.info("[RouterAgent] 输入: {}", question);

        // 第一层：关键词匹配（快、稳、零成本）
        String intent = matchByKeyword(question);
        double confidence = 0.95;

        // 第二层：LLM 兜底（关键词未命中时）
        if (intent.equals("UNKNOWN")) {
            intent = classifyByLlm(question);
            confidence = 0.75;
            log.info("[RouterAgent] 关键词未命中，LLM 兜底结果 intent={}", intent);
        }

        ctx.setIntent(intent);
        ctx.setRouterConfidence(confidence);
        ctx.getExecutedAgents().add(getName());
        log.info("[RouterAgent] 输出 intent={} confidence={}", intent, confidence);
    }

    /** 关键词规则：覆盖 95% 常见问法，剩余走 LLM 兜底 */
    private String matchByKeyword(String question) {
        if (question == null || question.isBlank()) return "UNKNOWN";
        String q = question.toLowerCase();

        // ORDER：工单关键词
        if (q.contains("工单") || q.contains("订单状态") || q.contains("ticket")
                || q.contains("处理中") || q.contains("已处理") || q.contains("未处理")) {
            return "ORDER";
        }
        // ANALYSIS：分析/对比/趋势关键词
        if (q.contains("分析") || q.contains("对比") || q.contains("趋势")
                || q.contains("流失") || q.contains("异常") || q.contains("为什么")
                || q.contains("环比") || q.contains("同比") || q.contains("增长")
                || q.contains("下降") || q.contains("变化") || q.contains("预测")) {
            return "ANALYSIS";
        }
        // CODE：代码生成（优先级低于 REPORT，放在前面避免误判）
        if (q.contains("写代码") || q.contains("生成代码") || q.contains("写个脚本")
                || q.contains("python脚本") || q.contains("java类")) {
            return "CODE";
        }

        // REPORT：数据查询关键词（大幅扩展，覆盖所有常见业务术语）
        // 查询类动词
        if (q.contains("查询") || q.contains("查") || q.contains("统计") || q.contains("汇总")
                || q.contains("显示") || q.contains("列出") || q.contains("找出")
                || q.contains("看看") || q.contains("帮我") || q.contains("给我")
                || q.contains("计算") || q.contains("求") || q.contains("获取")) {
            return "REPORT";
        }
        // 金额/数量类（核心业务指标）
        if (q.contains("多少") || q.contains("几个") || q.contains("总数") || q.contains("总额")
                || q.contains("金额") || q.contains("交易额") || q.contains("交易金额")
                || q.contains("收益") || q.contains("收入") || q.contains("利润")
                || q.contains("手续费") || q.contains("笔数") || q.contains("交易量")
                || q.contains("交易笔数") || q.contains("数量") || q.contains("数目")) {
            return "REPORT";
        }
        // 排名/排序类
        if (q.contains("排名") || q.contains("前") || q.contains("TOP") || q.contains("top")
                || q.contains("最大") || q.contains("最小") || q.contains("最高") || q.contains("最低")
                || q.contains("排序") || q.contains("降序") || q.contains("升序")) {
            return "REPORT";
        }
        // 实体类（商户/部门/城市/员工）
        if (q.contains("商户") || q.contains("部门") || q.contains("城市")
                || q.contains("员工") || q.contains("客户") || q.contains("代理商")
                || q.contains("收银宝") || q.contains("收付通") || q.contains("邮政")
                || q.contains("归属") || q.contains("拓展") || q.contains("维护")) {
            return "REPORT";
        }
        // 列表/分布类
        if (q.contains("列表") || q.contains("有哪些") || q.contains("分布")
                || q.contains("明细") || q.contains("清单") || q.contains("一览")
                || q.contains("各") || q.contains("每个") || q.contains("分别")) {
            return "REPORT";
        }
        // 时间类（通常跟在数据查询后面）
        if (q.contains("上个月") || q.contains("上月") || q.contains("本月")
                || q.contains("上周") || q.contains("本周") || q.contains("今天")
                || q.contains("昨天") || q.contains("今年") || q.contains("去年")
                || q.contains("近") || q.contains("最近")) {
            return "REPORT";
        }

        return "UNKNOWN";
    }

    /** LLM 兜底：调 DeepSeek 做意图分类，失败时默认 REPORT（大多数问题是数据查询） */
    private String classifyByLlm(String question) {
        try {
            RoutingAssistant router = dev.langchain4j.service.AiServices.builder(RoutingAssistant.class)
                    .chatModel(chatModel)
                    .build();
            String raw = router.route(question);
            log.info("[RouterAgent] LLM 原始输出 (len={}): {}", raw.length(),
                    raw.length() > 200 ? raw.substring(0, 200) + "..." : raw);
            String result = parseIntent(raw);
            if (!"UNKNOWN".equals(result)) {
                return result;
            }
        } catch (Exception e) {
            log.warn("[RouterAgent] LLM 调用失败: {}", e.getMessage());
        }
        // LLM 也无法判断时，默认走 REPORT（用户问的大多是数据查询）
        log.info("[RouterAgent] LLM 兜底未命中，默认 REPORT");
        return "REPORT";
    }

    private String parseIntent(String raw) {
        if (raw == null || raw.isBlank()) return "UNKNOWN";
        String text = raw.trim();

        if (text.startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(text);
                String v = node.path("intent").asText("UNKNOWN").toUpperCase();
                if (VALID_INTENTS.contains(v)) return v;
            } catch (Exception ignored) {}
        }

        Matcher m = INTENT_PATTERN.matcher(raw);
        if (m.find()) return m.group(1).toUpperCase();

        String upper = text.toUpperCase().replaceAll("[^A-Z_]", "");
        if (VALID_INTENTS.contains(upper) && upper.length() <= 10) return upper;

        return "UNKNOWN";
    }
}
