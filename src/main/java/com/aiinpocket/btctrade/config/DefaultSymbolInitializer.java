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

/**
 * 應用啟動時確保預設追蹤幣對存在。
 * 若 BTCUSDT 不存在則自動新增並觸發歷史資料同步。
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
        if (trackedSymbolService.getBySymbol(DEFAULT_SYMBOL).isPresent()) {
            log.debug("[預設幣對] {} 已存在，跳過初始化", DEFAULT_SYMBOL);
            return;
        }

        TrackedSymbol saved = trackedSymbolService.addSymbol(DEFAULT_SYMBOL, null);
        log.info("[預設幣對] 已自動新增 {}，開始歷史資料同步", DEFAULT_SYMBOL);

        if (saved.getSyncStatus() == SyncStatus.PENDING) {
            historicalSyncService.syncHistoricalData(DEFAULT_SYMBOL);
        }
    }
}
