package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.BinanceApiProperties;
import com.aiinpocket.btctrade.model.dto.BinanceKlineResponse;
import com.aiinpocket.btctrade.model.entity.Kline;
import com.aiinpocket.btctrade.repository.KlineRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BinanceApiService {

    private final RestClient binanceRestClient;
    private final BinanceApiProperties props;
    private final KlineRepository klineRepository;
    private final ObjectMapper objectMapper;

    public List<Kline> fetchAndStoreHistoricalData(
            String symbol, String interval,
            Instant startTime, Instant endTime) {

        List<Kline> allKlines = new ArrayList<>();
        Instant currentStart = startTime;

        while (currentStart.isBefore(endTime)) {
            List<BinanceKlineResponse> batch = fetchKlines(
                    symbol, interval,
                    currentStart.toEpochMilli(),
                    endTime.toEpochMilli(),
                    1000);

            if (batch.isEmpty()) break;

            List<Kline> entities = batch.stream()
                    .map(r -> mapToEntity(r, symbol, interval))
                    .toList();

            List<Kline> saved = saveNewKlines(entities);
            allKlines.addAll(saved);

            long lastCloseTime = batch.getLast().closeTime();
            currentStart = Instant.ofEpochMilli(lastCloseTime + 1);

            sleep(props.rateLimitMs());
        }

        log.info("Fetched and stored {} klines for {} [{}]",
                allKlines.size(), symbol, interval);
        return allKlines;
    }

    public Optional<Kline> fetchLatestKline(String symbol, String interval) {
        List<BinanceKlineResponse> result = fetchKlines(
                symbol, interval, null, null, 1);
        if (result.isEmpty()) return Optional.empty();

        Kline entity = mapToEntity(result.getFirst(), symbol, interval);
        // 冪等寫入：已存在則跳過
        if (klineRepository.existsBySymbolAndIntervalTypeAndOpenTime(
                entity.getSymbol(), entity.getIntervalType(), entity.getOpenTime())) {
            return Optional.of(entity);
        }
        return Optional.of(klineRepository.save(entity));
    }

    private List<BinanceKlineResponse> fetchKlines(
            String symbol, String interval,
            Long startTime, Long endTime, int limit) {
        try {
            String json = binanceRestClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path(props.klinesPath())
                                .queryParam("symbol", symbol)
                                .queryParam("interval", interval)
                                .queryParam("limit", limit);
                        if (startTime != null) builder.queryParam("startTime", startTime);
                        if (endTime != null) builder.queryParam("endTime", endTime);
                        return builder.build();
                    })
                    .retrieve()
                    .body(String.class);

            List<List<Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            if (raw == null) return List.of();

            return raw.stream().map(arr -> new BinanceKlineResponse(
                    ((Number) arr.get(0)).longValue(),
                    new BigDecimal(arr.get(1).toString()),
                    new BigDecimal(arr.get(2).toString()),
                    new BigDecimal(arr.get(3).toString()),
                    new BigDecimal(arr.get(4).toString()),
                    new BigDecimal(arr.get(5).toString()),
                    ((Number) arr.get(6)).longValue(),
                    new BigDecimal(arr.get(7).toString()),
                    ((Number) arr.get(8)).intValue(),
                    new BigDecimal(arr.get(9).toString()),
                    new BigDecimal(arr.get(10).toString())
            )).toList();

        } catch (Exception e) {
            log.error("Failed to fetch klines from Binance: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private Kline mapToEntity(BinanceKlineResponse r, String symbol, String interval) {
        return Kline.builder()
                .symbol(symbol)
                .intervalType(interval)
                .openTime(Instant.ofEpochMilli(r.openTime()))
                .closeTime(Instant.ofEpochMilli(r.closeTime()))
                .openPrice(r.open())
                .highPrice(r.high())
                .lowPrice(r.low())
                .closePrice(r.close())
                .volume(r.volume())
                .quoteVolume(r.quoteVolume())
                .tradeCount(r.tradeCount())
                .takerBuyBaseVolume(r.takerBuyBaseVolume())
                .takerBuyQuoteVolume(r.takerBuyQuoteVolume())
                .build();
    }

    /**
     * 帶進度回調的歷史資料同步（供 HistoricalSyncService 使用）。
     * 每批次獨立 commit，避免長時間事務造成效能瓶頸。
     */
    public List<Kline> fetchAndStoreHistoricalDataWithProgress(
            String symbol, String interval,
            Instant startTime, Instant endTime,
            java.util.function.BiConsumer<Integer, Integer> progressCallback) {

        List<Kline> allKlines = new ArrayList<>();
        Instant currentStart = startTime;
        int batchCount = 0;

        // 估算總批次數
        long totalMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
        long intervalMinutes = switch (interval) {
            case "1m" -> 1; case "5m" -> 5; case "15m" -> 15;
            case "1h" -> 60; case "4h" -> 240; case "1d" -> 1440;
            default -> 5;
        };
        int estimatedBatches = Math.max(1, (int) Math.ceil((double) totalMinutes / intervalMinutes / 1000));

        while (currentStart.isBefore(endTime)) {
            List<BinanceKlineResponse> batch = fetchKlines(
                    symbol, interval,
                    currentStart.toEpochMilli(),
                    endTime.toEpochMilli(),
                    1000);

            if (batch.isEmpty()) break;

            List<Kline> entities = batch.stream()
                    .map(r -> mapToEntity(r, symbol, interval))
                    .toList();

            List<Kline> saved = saveNewKlines(entities);
            allKlines.addAll(saved);

            batchCount++;
            if (progressCallback != null) {
                progressCallback.accept(batchCount, estimatedBatches);
            }

            long lastCloseTime = batch.getLast().closeTime();
            currentStart = Instant.ofEpochMilli(lastCloseTime + 1);

            sleep(props.rateLimitMs());
        }

        log.info("Fetched and stored {} klines for {} [{}] in {} batches",
                allKlines.size(), symbol, interval, batchCount);
        return allKlines;
    }

    private List<Kline> saveNewKlines(List<Kline> klines) {
        List<Kline> toSave = klines.stream()
                .filter(k -> !klineRepository.existsBySymbolAndIntervalTypeAndOpenTime(
                        k.getSymbol(), k.getIntervalType(), k.getOpenTime()))
                .toList();
        return klineRepository.saveAll(toSave);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
