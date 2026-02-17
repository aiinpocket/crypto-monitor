package com.aiinpocket.btctrade.model.dto;

import java.math.BigDecimal;

/**
 * Binance Kline API 完整 12 欄位：
 * [0] openTime, [1] open, [2] high, [3] low, [4] close,
 * [5] volume, [6] closeTime, [7] quoteAssetVolume,
 * [8] numberOfTrades, [9] takerBuyBaseAssetVolume,
 * [10] takerBuyQuoteAssetVolume, [11] unused
 */
public record BinanceKlineResponse(
        long openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        long closeTime,
        BigDecimal quoteVolume,
        int tradeCount,
        BigDecimal takerBuyBaseVolume,
        BigDecimal takerBuyQuoteVolume
) {}
