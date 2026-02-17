package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.BinanceApiProperties;
import com.aiinpocket.btctrade.config.IntervalConfig;
import com.aiinpocket.btctrade.config.TradingStrategyProperties;
import com.aiinpocket.btctrade.model.dto.IndicatorSnapshot;
import com.aiinpocket.btctrade.model.entity.Kline;
import com.aiinpocket.btctrade.model.enums.SyncStatus;
import com.aiinpocket.btctrade.model.event.KlineClosed;
import com.aiinpocket.btctrade.repository.KlineRepository;
import com.aiinpocket.btctrade.repository.TrackedSymbolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;

import java.util.List;

/**
 * K 線收盤事件監聽器。
 * 當 WebSocket 串流偵測到一根 K 線收盤時觸發，載入歷史資料並執行策略評估。
 * 使用 {@link IntervalConfig.IntervalParams} 動態計算 bar 時長，
 * 不再硬編碼特定的時間間隔值。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KlineClosedEventHandler {

    private final KlineRepository klineRepo;
    private final BarSeriesFactory barSeriesFactory;
    private final TechnicalIndicatorService indicatorService;
    private final TradeExecutionService tradeExecutionService;
    private final TradingStrategyProperties props;
    private final TrackedSymbolRepository trackedSymbolRepo;
    private final BinanceApiProperties apiProperties;
    private final IntervalConfig.IntervalParams intervalParams;

    @EventListener
    public void onKlineClosed(KlineClosed event) {
        String symbol = event.symbol();

        // 只對 active + READY 的符號執行策略
        var tracked = trackedSymbolRepo.findBySymbol(symbol);
        if (tracked.isEmpty() || !tracked.get().isActive()
                || tracked.get().getSyncStatus() != SyncStatus.READY) {
            return;
        }

        try {
            // 載入最近 N 根 K 線（足夠計算指標即可）
            int lookback = Math.max(props.strategy().emaLong() * 3, 200);
            List<Kline> recentKlines = klineRepo
                    .findBySymbolAndIntervalTypeAndOpenTimeBetweenOrderByOpenTimeAsc(
                            symbol, apiProperties.defaultInterval(),
                            event.kline().getOpenTime().minusSeconds(
                                    lookback * intervalParams.barDurationMinutes() * 60),
                            event.kline().getCloseTime());

            if (recentKlines.size() < props.strategy().emaLong() + 10) {
                log.debug("Not enough klines for strategy evaluation: {} (need {})",
                        recentKlines.size(), props.strategy().emaLong() + 10);
                return;
            }

            BarSeries series = barSeriesFactory.createFromKlines(recentKlines, "live-" + symbol);
            int lastIndex = series.getBarCount() - 1;
            IndicatorSnapshot snapshot = indicatorService.computeAt(series, lastIndex);

            tradeExecutionService.evaluateAndExecute(symbol, snapshot);

        } catch (Exception e) {
            log.error("策略評估失敗 for {}: {}", symbol, e.getMessage(), e);
        }
    }
}
