package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.BinanceApiProperties;
import com.aiinpocket.btctrade.model.dto.ExchangePairInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Binance ExchangeInfo 查詢服務。
 * 從 /api/v3/exchangeInfo 取得可用交易對，結果快取 1 小時。
 * 主要用途：
 * <ul>
 *   <li>前端搜尋下拉選單 — 列出所有可用 USDT 交易對</li>
 *   <li>下架偵測 — 檢查幣對是否仍在 TRADING 狀態</li>
 * </ul>
 */
@Service
@Slf4j
public class BinanceExchangeInfoService {

    private final RestClient binanceRestClient;
    private final BinanceApiProperties apiProperties;
    private final ObjectMapper objectMapper;

    public BinanceExchangeInfoService(
            RestClient binanceRestClient,
            BinanceApiProperties apiProperties,
            ObjectMapper objectMapper) {
        this.binanceRestClient = binanceRestClient;
        this.apiProperties = apiProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 取得所有可用的 USDT 交易對（快取 1 小時）。
     * 過濾條件：quoteAsset=USDT 且 status=TRADING
     */
    @Cacheable("exchangeInfo")
    public List<ExchangePairInfo> getAvailablePairs() {
        log.info("正在從 Binance 取得 ExchangeInfo（此呼叫結果將被快取 1 小時）");
        try {
            String responseBody = binanceRestClient.get()
                    .uri(apiProperties.exchangeInfoPath())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode symbols = root.get("symbols");

            List<ExchangePairInfo> pairs = new ArrayList<>();
            if (symbols != null && symbols.isArray()) {
                for (JsonNode symbolNode : symbols) {
                    String status = symbolNode.get("status").asText();
                    String quoteAsset = symbolNode.get("quoteAsset").asText();

                    if ("TRADING".equals(status) && "USDT".equals(quoteAsset)) {
                        pairs.add(new ExchangePairInfo(
                                symbolNode.get("symbol").asText(),
                                symbolNode.get("baseAsset").asText(),
                                quoteAsset
                        ));
                    }
                }
            }

            log.info("取得 {} 個可用 USDT 交易對", pairs.size());
            return pairs;

        } catch (Exception e) {
            log.error("取得 ExchangeInfo 失敗: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 檢查指定幣對是否仍在 Binance 上交易中。
     * 用於下架偵測：連續錯誤超過閾值後呼叫此方法確認。
     *
     * @param symbol 交易對符號（如 BTCUSDT）
     * @return true 表示仍在 TRADING 狀態
     */
    public boolean isSymbolTrading(String symbol) {
        List<ExchangePairInfo> pairs = getAvailablePairs();
        return pairs.stream().anyMatch(p -> p.symbol().equals(symbol));
    }
}
