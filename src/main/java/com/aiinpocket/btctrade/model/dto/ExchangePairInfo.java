package com.aiinpocket.btctrade.model.dto;

/**
 * Binance 交易對資訊 DTO。
 * 從 /api/v3/exchangeInfo 回應中提取的可用交易對。
 *
 * @param symbol     交易對符號（如 BTCUSDT）
 * @param baseAsset  基礎資產（如 BTC）
 * @param quoteAsset 報價資產（如 USDT）
 */
public record ExchangePairInfo(
        String symbol,
        String baseAsset,
        String quoteAsset
) {}
