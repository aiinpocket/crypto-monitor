package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.BinanceApiProperties;
import com.aiinpocket.btctrade.model.enums.SyncStatus;
import com.aiinpocket.btctrade.websocket.TradeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricalSyncService {

    private final BinanceApiService binanceApiService;
    private final TrackedSymbolService trackedSymbolService;
    private final TradeWebSocketHandler wsHandler;
    private final BinanceApiProperties apiProperties;
    private final BinanceStreamManager binanceStreamManager;

    /** 固定起始日期：所有幣對從 2021-01-01 開始同步資料 */
    private static final Instant FIXED_START_DATE =
            LocalDate.of(2021, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();

    /**
     * 從固定起始日期（2021-01-01）同步歷史資料。
     */
    @Async("historicalSyncExecutor")
    public void syncHistoricalData(String symbol) {
        log.info("開始歷史資料同步: {}（從 2021-01-01 至今）", symbol);

        try {
            trackedSymbolService.updateSyncStatus(symbol, SyncStatus.SYNCING, 0, null);
            wsHandler.broadcastSyncProgress(symbol, 0, SyncStatus.SYNCING);

            Instant endDate = Instant.now();
            Instant startDate = FIXED_START_DATE;
            String interval = apiProperties.defaultInterval();

            binanceApiService.fetchAndStoreHistoricalDataWithProgress(
                    symbol, interval, startDate, endDate,
                    (batchDone, totalBatches) -> {
                        int progress = Math.min(99, (int) (100.0 * batchDone / totalBatches));
                        trackedSymbolService.updateSyncStatus(symbol, SyncStatus.SYNCING, progress, null);
                        wsHandler.broadcastSyncProgress(symbol, progress, SyncStatus.SYNCING);
                    });

            trackedSymbolService.updateSyncStatus(symbol, SyncStatus.READY, 100, null);
            wsHandler.broadcastSyncProgress(symbol, 100, SyncStatus.READY);

            // 同步完成後啟動 WebSocket 串流
            binanceStreamManager.onSymbolReady(symbol);
            log.info("歷史資料同步完成: {}", symbol);

        } catch (Exception e) {
            log.error("歷史資料同步失敗: {}", symbol, e);
            trackedSymbolService.updateSyncStatus(symbol, SyncStatus.ERROR, null,
                    e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "Unknown error");
            wsHandler.broadcastSyncProgress(symbol, -1, SyncStatus.ERROR);
        }
    }
}
