package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.BinanceApiProperties;
import com.aiinpocket.btctrade.config.IntervalConfig;
import com.aiinpocket.btctrade.config.TradingStrategyProperties;
import com.aiinpocket.btctrade.model.dto.IndicatorSnapshot;
import com.aiinpocket.btctrade.model.entity.Kline;
import com.aiinpocket.btctrade.model.entity.StrategyTemplate;
import com.aiinpocket.btctrade.model.enums.SyncStatus;
import com.aiinpocket.btctrade.model.event.KlineClosed;
import com.aiinpocket.btctrade.repository.AppUserRepository;
import com.aiinpocket.btctrade.repository.KlineRepository;
import com.aiinpocket.btctrade.repository.StrategyTemplateRepository;
import com.aiinpocket.btctrade.repository.TrackedSymbolRepository;
import com.aiinpocket.btctrade.repository.UserWatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;

import java.util.List;

/**
 * K 線收盤事件監聽器。
 * Phase 2: 遍歷所有有啟用策略且觀察此幣對的用戶，為每位用戶獨立評估策略。
 * BarSeries 每幣對只建一次（共享），指標和策略評估按用戶參數獨立計算。
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
    private final UserWatchlistRepository watchlistRepo;
    private final AppUserRepository userRepo;
    private final StrategyTemplateRepository templateRepo;

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

            // BarSeries 每幣對只建一次（共享）
            BarSeries series = barSeriesFactory.createFromKlines(recentKlines, "live-" + symbol);

            // Phase 2: 遍歷所有有啟用策略且觀察此幣對的用戶
            List<Long> activeUserIds = watchlistRepo.findActiveStrategyUserIdsBySymbol(symbol);

            if (activeUserIds.isEmpty()) {
                log.debug("[策略評估] 幣對 {} 無啟用策略的用戶訂閱", symbol);
                return;
            }

            log.debug("[策略評估] 幣對 {} 共 {} 位用戶需要評估", symbol, activeUserIds.size());

            for (Long userId : activeUserIds) {
                try {
                    evaluateForUser(userId, symbol, series);
                } catch (Exception e) {
                    log.error("[策略評估] userId={} 幣對 {} 評估失敗: {}",
                            userId, symbol, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("策略評估失敗 for {}: {}", symbol, e.getMessage(), e);
        }
    }

    private void evaluateForUser(Long userId, String symbol, BarSeries series) {
        // 1. 查詢用戶的啟用策略模板
        var user = userRepo.findById(userId).orElse(null);
        if (user == null || user.getActiveStrategyTemplateId() == null) return;

        var template = templateRepo.findById(user.getActiveStrategyTemplateId()).orElse(null);
        if (template == null) return;

        // 2. 轉換為用戶自訂參數
        TradingStrategyProperties userProps = template.toProperties();

        // 3. 建立臨時的指標和策略服務（無狀態，可安全 new）
        TechnicalIndicatorService userIndicator = new TechnicalIndicatorService(userProps);
        StrategyService userStrategy = new StrategyService(userProps, intervalParams);

        // 4. 計算用戶特定參數的指標快照
        int lastIndex = series.getBarCount() - 1;
        IndicatorSnapshot snapshot = userIndicator.computeAt(series, lastIndex);

        // 5. 用戶獨立交易評估
        tradeExecutionService.evaluateAndExecuteForUser(
                userId, symbol, snapshot, userProps, userStrategy);
    }
}
