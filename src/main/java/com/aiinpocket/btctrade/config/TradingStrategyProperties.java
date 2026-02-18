package com.aiinpocket.btctrade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "trading")
public record TradingStrategyProperties(
        StrategyParams strategy,
        RiskParams risk,
        RsiParams rsi
) {
    public record StrategyParams(
            int emaShort,
            int emaLong,
            int rsiPeriod,
            int macdShort,
            int macdLong,
            int macdSignal,
            int donchianEntry,
            int donchianExit
    ) {}

    public record RiskParams(
            double stopLossPct,
            int maxHoldingDays,
            double initialCapital,
            int maxTradesPerDay,
            int leverage,
            double trailingActivatePct,
            double trailingOffsetPct,
            int timeStopDays,
            int cooldownDays,
            double positionSizePct
    ) {}

    public record RsiParams(
            double longEntryMin,
            double longEntryMax,
            double shortEntryMin,
            double shortEntryMax,
            double longExitExtreme,
            double shortExitExtreme
    ) {}
}
