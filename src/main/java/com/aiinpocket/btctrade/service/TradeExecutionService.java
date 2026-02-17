package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.IntervalConfig.IntervalParams;
import com.aiinpocket.btctrade.config.TradingStrategyProperties;
import com.aiinpocket.btctrade.model.dto.IndicatorSnapshot;
import com.aiinpocket.btctrade.model.dto.TradeNotification;
import com.aiinpocket.btctrade.model.entity.TradePosition;
import com.aiinpocket.btctrade.model.enums.*;
import com.aiinpocket.btctrade.repository.TradePositionRepository;
import com.aiinpocket.btctrade.service.notification.NotificationDispatcher;
import com.aiinpocket.btctrade.websocket.TradeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 交易執行服務。
 * 接收 K 線收盤事件後，評估策略並執行進場/出場操作。
 * 產生交易訊號時會同步執行：
 * 1. WebSocket 廣播（即時更新前端）
 * 2. 通知分發（Discord / Gmail / Telegram，非同步）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeExecutionService {

    private final TradePositionRepository positionRepo;
    private final PositionService positionService;
    private final StrategyService strategyService;
    private final TradingStrategyProperties props;
    private final IntervalParams intervalParams;
    private final TradeWebSocketHandler wsHandler;
    private final NotificationDispatcher notificationDispatcher;

    /**
     * 評估策略並執行交易。
     * 被 KlineClosedEventHandler 在每根 K 線收盤時呼叫。
     */
    public void evaluateAndExecute(String symbol, IndicatorSnapshot snapshot) {
        var openPosition = positionRepo
                .findBySymbolAndStatus(symbol, PositionStatus.OPEN)
                .orElse(null);

        TradeAction action = strategyService.evaluate(snapshot, openPosition, Instant.now());

        if (action != TradeAction.HOLD) {
            log.info("[交易執行] {} 產生訊號: {} @ ${}", symbol, action, snapshot.closePrice());
            executeAction(action, symbol, openPosition, snapshot);

            // WebSocket 廣播訊號到所有連線的前端
            wsHandler.broadcastSignal(action, snapshot);

            // 非同步分發通知到所有訂閱此幣對的使用者
            TradeNotification notification = new TradeNotification(
                    symbol, action, snapshot.closePrice(),
                    snapshot.rsi(), snapshot.macdHistogram(), Instant.now());
            notificationDispatcher.notifyAllSubscribers(symbol, notification);
        }
    }

    private void executeAction(TradeAction action, String symbol,
                               TradePosition openPosition, IndicatorSnapshot snapshot) {
        Instant now = Instant.now();

        switch (action) {
            case LONG_ENTRY, SHORT_ENTRY -> {
                if (openPosition == null) {
                    PositionDirection dir = action == TradeAction.LONG_ENTRY
                            ? PositionDirection.LONG : PositionDirection.SHORT;
                    BigDecimal capital = calculateAvailableCapital();
                    positionService.openPosition(
                            symbol, dir, snapshot.closePrice(), now,
                            capital, snapshot, false);
                }
            }
            case LONG_EXIT, SHORT_EXIT -> {
                if (openPosition != null) {
                    ExitReason reason = determineExitReason(snapshot, openPosition, now);
                    positionService.closePosition(
                            openPosition, snapshot.closePrice(), now,
                            reason, snapshot);
                }
            }
            default -> { /* HOLD — 不執行任何操作 */ }
        }
    }

    /** 計算當前可用資本（初始資金 + 所有已實現損益） */
    private BigDecimal calculateAvailableCapital() {
        List<TradePosition> closedPositions = positionRepo
                .findByBacktestOrderByEntryTimeAsc(false);

        BigDecimal totalPnl = closedPositions.stream()
                .filter(p -> p.getRealizedPnl() != null)
                .map(TradePosition::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BigDecimal.valueOf(props.risk().initialCapital()).add(totalPnl);
    }

    /** 判斷出場原因（停損 / 最長持倉 / RSI 極端 / 訊號反轉） */
    private ExitReason determineExitReason(
            IndicatorSnapshot snap, TradePosition pos, Instant currentTime) {

        boolean isLong = pos.getDirection() == PositionDirection.LONG;
        double currentPrice = snap.closePrice().doubleValue();
        double entryPrice = pos.getEntryPrice().doubleValue();
        double rsi = snap.rsi().doubleValue();

        double priceDelta = isLong
                ? (currentPrice - entryPrice) / entryPrice
                : (entryPrice - currentPrice) / entryPrice;

        if (priceDelta <= -props.risk().stopLossPct()) return ExitReason.STOP_LOSS;

        long minutesHeld = Duration.between(pos.getEntryTime(), currentTime).toMinutes();
        long barsHeld = minutesHeld / intervalParams.barDurationMinutes();
        int maxHoldingBars = props.risk().maxHoldingDays() * intervalParams.barsPerDay();
        if (barsHeld >= maxHoldingBars) return ExitReason.MAX_HOLDING_PERIOD;

        if (isLong && rsi > props.rsi().longExitExtreme()) return ExitReason.RSI_EXTREME;
        if (!isLong && rsi < props.rsi().shortExitExtreme()) return ExitReason.RSI_EXTREME;

        return ExitReason.SIGNAL_REVERSAL;
    }
}
