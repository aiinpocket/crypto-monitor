package com.aiinpocket.btctrade.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BacktestReport(
        String symbol,
        Instant startDate,
        Instant endDate,
        int totalBars,

        int totalTrades,
        int winningTrades,
        int losingTrades,
        BigDecimal winRate,

        BigDecimal totalReturn,
        BigDecimal annualizedReturn,
        BigDecimal maxDrawdown,

        BigDecimal sharpeRatio,
        BigDecimal profitFactor,
        BigDecimal averageWin,
        BigDecimal averageLoss,
        int maxConsecutiveLosses,

        BigDecimal initialCapital,
        BigDecimal finalCapital,

        List<TradeDetail> trades,
        List<EquityCurvePoint> equityCurve,

        boolean passed
) {
    public record TradeDetail(
            int tradeNumber,
            String direction,
            Instant entryTime,
            Instant exitTime,
            BigDecimal entryPrice,
            BigDecimal exitPrice,
            BigDecimal pnl,
            BigDecimal returnPct,
            String exitReason,
            int holdingBars
    ) {}

    public record EquityCurvePoint(
            Instant time,
            BigDecimal equity
    ) {}
}
