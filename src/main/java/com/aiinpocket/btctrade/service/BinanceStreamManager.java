package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.TrackedSymbol;
import com.aiinpocket.btctrade.model.enums.SyncStatus;
import com.aiinpocket.btctrade.repository.TrackedSymbolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BinanceStreamManager implements SmartLifecycle {

    private final BinanceWebSocketClient wsClient;
    private final TrackedSymbolRepository trackedSymbolRepo;
    private volatile boolean running = false;

    @Override
    public void start() {
        List<TrackedSymbol> readySymbols = trackedSymbolRepo
                .findByActiveTrueAndSyncStatus(SyncStatus.READY);

        List<String> symbols = readySymbols.stream()
                .map(TrackedSymbol::getSymbol)
                .toList();

        if (!symbols.isEmpty()) {
            wsClient.subscribeAll(symbols);
        }

        running = true;
        log.info("BinanceStreamManager started, subscribed {} symbols: {}", symbols.size(), symbols);
    }

    @Override
    public void stop() {
        wsClient.closeAll();
        running = false;
        log.info("BinanceStreamManager stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE; // 最後啟動
    }

    /**
     * 新符號歷史同步完成後呼叫，啟動 WebSocket 串流。
     */
    public void onSymbolReady(String symbol) {
        log.info("Symbol {} is ready, subscribing to WebSocket", symbol);
        wsClient.subscribe(symbol);
    }

    /**
     * 幣對下架後呼叫，停止 WebSocket 串流。
     */
    public void onSymbolDelisted(String symbol) {
        log.warn("Symbol {} is delisted, unsubscribing from WebSocket", symbol);
        wsClient.unsubscribe(symbol);
    }
}
