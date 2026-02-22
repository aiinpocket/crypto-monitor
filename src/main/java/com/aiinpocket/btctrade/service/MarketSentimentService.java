package com.aiinpocket.btctrade.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 市場情緒服務。
 * 從 Alternative.me API 取得 Fear & Greed Index，提供市場情緒指標。
 * 內建 10 分鐘記憶體快取。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketSentimentService {

    private static final String FNG_API_URL = "https://api.alternative.me/fng/?limit=30&format=json";
    private static final long CACHE_TTL_SECONDS = 600; // 10 分鐘

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private volatile SentimentData cachedData;
    private volatile Instant cacheExpiry = Instant.EPOCH;

    /**
     * 取得目前的 Fear & Greed Index。
     */
    public SentimentData getSentiment() {
        if (Instant.now().isBefore(cacheExpiry) && cachedData != null) {
            return cachedData;
        }
        return refreshSentiment();
    }

    @SuppressWarnings("unchecked")
    private synchronized SentimentData refreshSentiment() {
        if (Instant.now().isBefore(cacheExpiry) && cachedData != null) {
            return cachedData;
        }

        try {
            String json = restClient.get()
                    .uri(FNG_API_URL)
                    .retrieve()
                    .body(String.class);

            if (json == null || json.isBlank()) {
                log.warn("[情緒] Alternative.me API 回應為空");
                return cachedData != null ? cachedData : SentimentData.empty();
            }

            Map<String, Object> response = objectMapper.readValue(json, new TypeReference<>() {});
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");

            if (dataList == null || dataList.isEmpty()) {
                log.warn("[情緒] Fear & Greed 資料為空");
                return cachedData != null ? cachedData : SentimentData.empty();
            }

            // 當前值
            Map<String, Object> current = dataList.getFirst();
            int currentValue = Integer.parseInt(current.get("value").toString());
            String classification = current.get("value_classification").toString();

            // 歷史資料（最近 30 天）
            List<SentimentPoint> history = new ArrayList<>();
            for (Map<String, Object> point : dataList) {
                int val = Integer.parseInt(point.get("value").toString());
                long ts = Long.parseLong(point.get("timestamp").toString());
                String cls = point.get("value_classification").toString();
                history.add(new SentimentPoint(val, cls, Instant.ofEpochSecond(ts)));
            }

            cachedData = new SentimentData(currentValue, classification, history);
            cacheExpiry = Instant.now().plusSeconds(CACHE_TTL_SECONDS);
            log.info("[情緒] Fear & Greed Index: {} ({})", currentValue, classification);
            return cachedData;

        } catch (Exception e) {
            log.error("[情緒] Fear & Greed Index 取得失敗: {}", e.getMessage());
            return cachedData != null ? cachedData : SentimentData.empty();
        }
    }

    // ===== Records =====

    public record SentimentData(
            int currentValue,
            String classification,
            List<SentimentPoint> history
    ) {
        static SentimentData empty() {
            return new SentimentData(50, "Neutral", List.of());
        }
    }

    public record SentimentPoint(
            int value,
            String classification,
            Instant timestamp
    ) {}
}
