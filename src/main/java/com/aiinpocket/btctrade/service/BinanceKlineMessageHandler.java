package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.Kline;
import com.aiinpocket.btctrade.model.event.KlineClosed;
import com.aiinpocket.btctrade.model.event.KlineTick;
import com.aiinpocket.btctrade.repository.KlineRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class BinanceKlineMessageHandler {

    private final KlineRepository klineRepo;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public void handleMessage(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // 處理 combined stream 格式: {"stream":"...","data":{...}}
            JsonNode data = root.has("data") ? root.get("data") : root;
            JsonNode klineData = data.get("k");

            if (klineData == null) {
                log.debug("Non-kline message received, ignoring");
                return;
            }

            boolean isClosed = klineData.get("x").asBoolean();
            String symbol = klineData.get("s").asText();
            String interval = klineData.get("i").asText();

            Kline kline = Kline.builder()
                    .symbol(symbol)
                    .intervalType(interval)
                    .openTime(Instant.ofEpochMilli(klineData.get("t").asLong()))
                    .closeTime(Instant.ofEpochMilli(klineData.get("T").asLong()))
                    .openPrice(new BigDecimal(klineData.get("o").asText()))
                    .highPrice(new BigDecimal(klineData.get("h").asText()))
                    .lowPrice(new BigDecimal(klineData.get("l").asText()))
                    .closePrice(new BigDecimal(klineData.get("c").asText()))
                    .volume(new BigDecimal(klineData.get("v").asText()))
                    .quoteVolume(new BigDecimal(klineData.get("q").asText()))
                    .tradeCount(klineData.get("n").asInt())
                    .takerBuyBaseVolume(new BigDecimal(klineData.get("V").asText()))
                    .takerBuyQuoteVolume(new BigDecimal(klineData.get("Q").asText()))
                    .build();

            if (isClosed) {
                // K 線收盤 → 存入 DB + 觸發策略評估
                if (!klineRepo.existsBySymbolAndIntervalTypeAndOpenTime(
                        symbol, interval, kline.getOpenTime())) {
                    klineRepo.save(kline);
                    log.debug("Saved closed kline: {} {} @ {}", symbol, interval, kline.getClosePrice());
                }
                eventPublisher.publishEvent(new KlineClosed(symbol, interval, kline));
            } else {
                // 未收盤 → 即時價格更新
                eventPublisher.publishEvent(new KlineTick(symbol, kline));
            }
        } catch (Exception e) {
            log.error("Failed to parse Binance WS message: {}", e.getMessage());
        }
    }
}
