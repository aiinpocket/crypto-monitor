package com.aiinpocket.btctrade.config;

import com.aiinpocket.btctrade.model.entity.TrackedSymbol;
import com.aiinpocket.btctrade.model.enums.SyncStatus;
import com.aiinpocket.btctrade.service.HistoricalSyncService;
import com.aiinpocket.btctrade.service.TrackedSymbolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 應用啟動時確保預設追蹤幣對存在且資料同步完成。
 * - 若 BTCUSDT 不存在：自動新增並觸發歷史資料同步
 * - 若 BTCUSDT 狀態為 SYNCING/PENDING/ERROR：重新觸發同步（處理重啟恢復）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultSymbolInitializer implements ApplicationRunner {

    private static final String DEFAULT_SYMBOL = "BTCUSDT";

    private final TrackedSymbolService trackedSymbolService;
    private final HistoricalSyncService historicalSyncService;

    @Override
    public void run(ApplicationArguments args) {
        Optional<TrackedSymbol> existing = trackedSymbolService.getBySymbol(DEFAULT_SYMBOL);

        if (existing.isEmpty()) {
            TrackedSymbol saved = trackedSymbolService.addSymbol(DEFAULT_SYMBOL, null);
            log.info("[預設幣對] 已自動新增 {}，開始歷史資料同步", DEFAULT_SYMBOL);
            if (saved.getSyncStatus() == SyncStatus.PENDING) {
                historicalSyncService.syncHistoricalData(DEFAULT_SYMBOL);
            }
            return;
        }

        TrackedSymbol symbol = existing.get();
        SyncStatus status = symbol.getSyncStatus();

        if (status == SyncStatus.READY) {
            log.debug("[預設幣對] {} 已就緒，跳過初始化", DEFAULT_SYMBOL);
            return;
        }

        // SYNCING / PENDING / ERROR → 重新觸發同步（處理前次中斷）
        log.info("[預設幣對] {} 狀態為 {}，重新觸發歷史資料同步", DEFAULT_SYMBOL, status);
        historicalSyncService.syncHistoricalData(DEFAULT_SYMBOL);
    }
}
