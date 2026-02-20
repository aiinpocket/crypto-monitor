package com.aiinpocket.btctrade.config;

import com.aiinpocket.btctrade.model.entity.TrackedSymbol;
import com.aiinpocket.btctrade.model.enums.SyncStatus;
import com.aiinpocket.btctrade.repository.TrackedSymbolRepository;
import com.aiinpocket.btctrade.service.HistoricalSyncService;
import com.aiinpocket.btctrade.service.TrackedSymbolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 應用啟動時確保預設追蹤幣對存在且資料同步完成。
 * - 若 BTCUSDT 不存在：自動新增並觸發歷史資料同步
 * - 所有 active 且非 READY/DELISTED 的幣對：重新觸發同步（處理重啟恢復）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultSymbolInitializer implements ApplicationRunner {

    private static final List<String> DEFAULT_SYMBOLS = List.of("BTCUSDT", "ETHUSDT");

    private final TrackedSymbolService trackedSymbolService;
    private final TrackedSymbolRepository trackedSymbolRepo;
    private final HistoricalSyncService historicalSyncService;

    @Override
    public void run(ApplicationArguments args) {
        ensureDefaultSymbols();
        recoverStuckSyncs();
    }

    private void ensureDefaultSymbols() {
        for (String symbol : DEFAULT_SYMBOLS) {
            Optional<TrackedSymbol> existing = trackedSymbolService.getBySymbol(symbol);
            if (existing.isEmpty()) {
                TrackedSymbol saved = trackedSymbolService.addSymbol(symbol, null);
                log.info("[預設幣對] 已自動新增 {}，開始歷史資料同步", symbol);
                if (saved.getSyncStatus() == SyncStatus.PENDING) {
                    historicalSyncService.syncHistoricalData(symbol);
                }
            }
        }
    }

    private void recoverStuckSyncs() {
        List<TrackedSymbol> stuckSymbols = trackedSymbolRepo.findByActiveTrue().stream()
                .filter(s -> s.getSyncStatus() != SyncStatus.READY && s.getSyncStatus() != SyncStatus.DELISTED)
                .toList();

        if (stuckSymbols.isEmpty()) {
            log.debug("[啟動恢復] 所有 active 幣對皆已就緒");
            return;
        }

        log.info("[啟動恢復] 發現 {} 個未完成同步的幣對，逐一重新觸發", stuckSymbols.size());
        for (TrackedSymbol symbol : stuckSymbols) {
            log.info("[啟動恢復] {} 狀態為 {}，重新觸發歷史資料同步", symbol.getSymbol(), symbol.getSyncStatus());
            historicalSyncService.syncHistoricalData(symbol.getSymbol());
        }
    }
}
