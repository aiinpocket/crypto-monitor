package com.aiinpocket.btctrade.model.dto;

import java.math.BigDecimal;

public record IndicatorSnapshot(
        BigDecimal emaShort,
        BigDecimal emaLong,
        BigDecimal rsi,
        BigDecimal macdValue,
        BigDecimal macdSignal,
        BigDecimal macdHistogram,
        BigDecimal closePrice,
        boolean emaGoldenCross,
        boolean emaDeathCross,
        boolean emaTrendBullish,
        boolean macdBullishCross,
        boolean macdBearishCross,
        BigDecimal adx,
        // Donchian Channel — 前一根 K 線的通道值（避免前瞻偏差）
        BigDecimal donchianHigh,   // 前一根 K 線的 N 日最高價
        BigDecimal donchianLow,    // 前一根 K 線的 N 日最低價
        BigDecimal donchianExitHigh, // 前一根 K 線的 M 日最高價（空頭出場用）
        BigDecimal donchianExitLow   // 前一根 K 線的 M 日最低價（多頭出場用）
) {}
