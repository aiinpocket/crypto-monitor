package com.aiinpocket.btctrade.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 策略績效摘要 DTO。
 * 用於前端策略對比表格，每個策略包含 7 個時段的績效指標。
 */
public record StrategyPerformanceSummary(
        Long templateId,
        String templateName,
        String description,
        boolean systemDefault,
        List<PeriodMetric> periods,
        Instant lastComputedAt
) {
    public record PeriodMetric(
            String periodKey,
            String periodLabel,
            BigDecimal winRate,
            BigDecimal totalReturn,
            BigDecimal annualizedReturn,
            BigDecimal maxDrawdown,
            BigDecimal sharpeRatio,
            int totalTrades,
            BigDecimal unrealizedPnlPct,
            String unrealizedDirection
    ) {}
}
