package com.aiinpocket.btctrade.job;

import com.aiinpocket.btctrade.config.BinanceApiProperties;
import com.aiinpocket.btctrade.model.entity.TrackedSymbol;
import com.aiinpocket.btctrade.service.BinanceApiService;
import com.aiinpocket.btctrade.service.BinanceExchangeInfoService;
import com.aiinpocket.btctrade.service.BinanceStreamManager;
import com.aiinpocket.btctrade.service.BinanceWebSocketClient;
import com.aiinpocket.btctrade.service.DistributedLockService;
import com.aiinpocket.btctrade.service.TrackedSymbolService;
import com.aiinpocket.btctrade.websocket.TradeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定時資料補充任務（每 5 分鐘）。
 * 遍歷所有 READY 狀態的幣對，補充可能缺失的最新 K 線。
 * 包含下架偵測：連續錯誤超過閾值時檢查 ExchangeInfo，確認下架則停止更新。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataFetchJob extends QuartzJobBean {

    private final BinanceApiService binanceApiService;
    private final BinanceApiProperties apiProperties;
    private final TrackedSymbolService trackedSymbolService;
    private final BinanceExchangeInfoService exchangeInfoService;
    private final BinanceStreamManager binanceStreamManager;
    private final BinanceWebSocketClient binanceWebSocketClient;
    private final TradeWebSocketHandler wsHandler;
    private final DistributedLockService lockService;

    /** Advisory lock ID: DataFetchJob 專用 */
    private static final long DATA_FETCH_LOCK_ID = 2_000_002L;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        lockService.executeWithLock(DATA_FETCH_LOCK_ID, "DataFetchJob", this::doFetch);
    }

    private void doFetch() {
        List<TrackedSymbol> symbols = trackedSymbolService.getSchedulableSymbols();
        if (symbols.isEmpty()) {
            log.debug("DataFetchJob: 無可排程的幣對");
            return;
        }

        log.debug("DataFetchJob: 開始處理 {} 個幣對", symbols.size());

        for (TrackedSymbol ts : symbols) {
            String symbol = ts.getSymbol();
            // WebSocket 已連線的幣對由即時串流處理，DataFetchJob 只補充缺失
            if (binanceWebSocketClient.isConnected(symbol)) {
                log.debug("DataFetch 跳過 {}: WebSocket 已連線", symbol);
                continue;
            }
            fetchForSymbol(symbol);
        }
    }

    private void fetchForSymbol(String symbol) {
        try {
            var kline = binanceApiService.fetchLatestKline(
                    symbol, apiProperties.defaultInterval());

            if (kline.isPresent()) {
                trackedSymbolService.resetErrorCount(symbol);
                log.debug("DataFetch 成功: {} close={}", symbol, kline.get().getClosePrice());
            } else {
                handleFetchFailure(symbol, "fetchLatestKline 返回空值");
            }
        } catch (Exception e) {
            handleFetchFailure(symbol, e.getMessage());
        }
    }

    private void handleFetchFailure(String symbol, String errorMsg) {
        int errorCount = trackedSymbolService.incrementErrorCount(symbol);
        int threshold = apiProperties.delistErrorThreshold();

        if (errorCount >= threshold) {
            log.warn("幣對 {} 連續 {} 次錯誤，檢查是否已下架...", symbol, errorCount);
            checkAndMarkDelisted(symbol);
        } else {
            log.warn("DataFetch 失敗: {} (連續第 {} 次): {}", symbol, errorCount, errorMsg);
        }
    }

    private void checkAndMarkDelisted(String symbol) {
        try {
            boolean trading = exchangeInfoService.isSymbolTrading(symbol);
            if (!trading) {
                trackedSymbolService.markAsDelisted(symbol);
                binanceStreamManager.onSymbolDelisted(symbol);
                wsHandler.broadcastDelistNotification(symbol);
                log.warn("幣對 {} 已確認下架，停止所有更新排程", symbol);
            } else {
                log.warn("幣對 {} 仍在 Binance 交易中，可能是暫時性 API 問題", symbol);
            }
        } catch (Exception e) {
            log.error("檢查幣對 {} 下架狀態時發生錯誤: {}", symbol, e.getMessage());
        }
    }
}
