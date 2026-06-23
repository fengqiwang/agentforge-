package com.agentforge.report.reconciliation;

import com.agentforge.common.model.reconciliation.ReconciliationResult;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationSummaryGenerator {

    private final ChatModel chatModel;

    public String generate(ReconciliationResult result) {
        if (result == null) return "无对账数据";
        String prompt = buildPrompt(result);
        try {
            String summary = chatModel.chat(prompt);
            log.info("AI摘要生成成功，长度={}", summary.length());
            return summary;
        } catch (Exception e) {
            log.warn("AI摘要生成失败，使用默认摘要：{}", e.getMessage());
            return buildFallbackSummary(result);
        }
    }

    private String buildPrompt(ReconciliationResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是对账分析专家。根据以下对账数据生成简洁的中文摘要报告（200字以内）：\n\n");
        sb.append("我方总笔数：").append(r.getTotalInternal()).append("\n");
        sb.append("第三方总笔数：").append(r.getTotalExternal()).append("\n");
        sb.append("匹配成功：").append(r.getMatchedCount()).append("笔\n");
        sb.append("金额差异：").append(r.getAmountDiffCount()).append("笔\n");
        sb.append("仅我方有：").append(r.getOnlyInternalCount()).append("笔\n");
        sb.append("仅第三方有：").append(r.getOnlyExternalCount()).append("笔\n");
        if (r.getInternalTotal() != null) {
            sb.append("我方总金额：").append(r.getInternalTotal()).append("元\n");
        }
        if (r.getExternalTotal() != null) {
            sb.append("第三方总金额：").append(r.getExternalTotal()).append("元\n");
        }
        if (r.getDiffAmount() != null && r.getDiffAmount().signum() > 0) {
            sb.append("差异金额：").append(r.getDiffAmount()).append("元\n");
        }
        sb.append("\n请包含：1)匹配率 2)差异分析 3)风险提示 4)处理建议");
        return sb.toString();
    }

    private String buildFallbackSummary(ReconciliationResult r) {
        int total = r.getTotalInternal() + r.getTotalExternal();
        double matchRate = total > 0 ? r.getMatchedCount() * 100.0 / total : 0;
        return String.format(
            "本次对账共处理%d笔交易，匹配成功%d笔（%.1f%%）。金额差异%d笔，仅我方%d笔，仅第三方%d笔。",
            total, r.getMatchedCount(), matchRate,
            r.getAmountDiffCount(), r.getOnlyInternalCount(), r.getOnlyExternalCount()
        );
    }
}
