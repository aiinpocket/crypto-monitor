package com.aiinpocket.btctrade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "binance.api")
public record BinanceApiProperties(
        String baseUrl,
        String klinesPath,
        String defaultSymbol,
        long rateLimitMs,
        String wsBaseUrl,
        String defaultInterval,
        String exchangeInfoPath,
        int delistErrorThreshold
) {}
