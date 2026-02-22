package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.BinanceApiProperties;
import com.aiinpocket.btctrade.model.entity.TrackedSymbol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 市場總覽服務。
 * 從 Binance API 取得 24hr 行情，提供多幣對市場概覽。
 * 內建 60 秒記憶體快取，避免頻繁請求 API。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketOverviewService {

    private final RestClient binanceRestClient;
    private final BinanceApiProperties props;
    private final TrackedSymbolService trackedSymbolService;
    private final ObjectMapper objectMapper;

    /** 快取 */
    private volatile List<MarketTicker> cachedTickers = List.of();
    private volatile Instant cacheExpiry = Instant.EPOCH;
    private static final long CACHE_TTL_SECONDS = 60;

    /**
     * 取得所有追蹤幣對的 24hr 行情。
     */
    public List<MarketTicker> getMarketOverview() {
        if (Instant.now().isBefore(cacheExpiry) && !cachedTickers.isEmpty()) {
            return cachedTickers;
        }
        return refreshMarketData();
    }

    /**
     * 強制刷新市場資料。
     */
    @SuppressWarnings("unchecked")
    public synchronized List<MarketTicker> refreshMarketData() {
        // Double-check after acquiring lock
        if (Instant.now().isBefore(cacheExpiry) && !cachedTickers.isEmpty()) {
            return cachedTickers;
        }

        try {
            // 取得所有追蹤幣對
            List<TrackedSymbol> tracked = trackedSymbolService.getReadySymbols();
            if (tracked.isEmpty()) return List.of();

            // 建立幣對名稱集合
            Set<String> trackedSymbols = new HashSet<>();
            for (TrackedSymbol ts : tracked) {
                trackedSymbols.add(ts.getSymbol());
            }

            // 呼叫 Binance ticker/24hr API（取所有 USDT 對）
            String json = binanceRestClient.get()
                    .uri(props.baseUrl() + "/api/v3/ticker/24hr")
                    .retrieve()
                    .body(String.class);

            if (json == null || json.isBlank()) {
                log.warn("[市場] Binance ticker API 回應為空");
                return cachedTickers;
            }

            List<Map<String, Object>> allTickers = objectMapper.readValue(
                    json, new TypeReference<List<Map<String, Object>>>() {});

            // 只保留追蹤的幣對
            List<MarketTicker> result = new ArrayList<>();
            for (Map<String, Object> t : allTickers) {
                String symbol = (String) t.get("symbol");
                if (symbol != null && trackedSymbols.contains(symbol)) {
                    result.add(new MarketTicker(
                            symbol,
                            parseBd(t.get("lastPrice")),
                            parseBd(t.get("priceChangePercent")),
                            parseBd(t.get("priceChange")),
                            parseBd(t.get("highPrice")),
                            parseBd(t.get("lowPrice")),
                            parseBd(t.get("volume")),
                            parseBd(t.get("quoteVolume")),
                            parseBd(t.get("openPrice")),
                            Long.parseLong(t.getOrDefault("count", "0").toString())
                    ));
                }
            }

            // 按漲跌幅排序
            result.sort((a, b) -> b.priceChangePercent().compareTo(a.priceChangePercent()));

            cachedTickers = List.copyOf(result);
            cacheExpiry = Instant.now().plusSeconds(CACHE_TTL_SECONDS);

            log.info("[市場] 刷新 {} 個幣對行情", result.size());
            return cachedTickers;

        } catch (Exception e) {
            log.error("[市場] 取得 24hr 行情失敗", e);
            return cachedTickers; // 回傳舊快取
        }
    }

    /**
     * 取得市場摘要統計。
     */
    public MarketSummary getMarketSummary() {
        List<MarketTicker> tickers = getMarketOverview();
        if (tickers.isEmpty()) {
            return new MarketSummary(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        int gainers = 0, losers = 0;
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal avgChange = BigDecimal.ZERO;

        for (MarketTicker t : tickers) {
            if (t.priceChangePercent().compareTo(BigDecimal.ZERO) > 0) gainers++;
            else if (t.priceChangePercent().compareTo(BigDecimal.ZERO) < 0) losers++;
            totalVolume = totalVolume.add(t.quoteVolume());
            avgChange = avgChange.add(t.priceChangePercent());
        }

        if (!tickers.isEmpty()) {
            avgChange = avgChange.divide(BigDecimal.valueOf(tickers.size()), 2, java.math.RoundingMode.HALF_UP);
        }

        return new MarketSummary(tickers.size(), gainers, losers, totalVolume, avgChange);
    }

    private static BigDecimal parseBd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    // ===== Records =====

    public record MarketTicker(
            String symbol,
            BigDecimal lastPrice,
            BigDecimal priceChangePercent,
            BigDecimal priceChange,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal volume,
            BigDecimal quoteVolume,
            BigDecimal openPrice,
            long tradeCount
    ) {}

    public record MarketSummary(
            int totalSymbols, int gainers, int losers,
            BigDecimal totalQuoteVolume, BigDecimal avgChangePercent
    ) {}
}
