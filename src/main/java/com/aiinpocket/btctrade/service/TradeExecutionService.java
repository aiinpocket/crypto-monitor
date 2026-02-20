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
 * 支援全域評估（舊邏輯）和每用戶獨立評估（Phase 2）。
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
     * 全域評估策略並執行交易（舊邏輯，保留向下相容）。
     */
    public void evaluateAndExecute(String symbol, IndicatorSnapshot snapshot) {
        var openPosition = positionRepo
                .findBySymbolAndStatus(symbol, PositionStatus.OPEN)
                .orElse(null);

        TradeAction action = strategyService.evaluate(snapshot, openPosition, Instant.now());

        if (action != TradeAction.HOLD) {
            log.info("[交易執行] {} 產生訊號: {} @ ${}", symbol, action, snapshot.closePrice());
            executeAction(null, action, symbol, openPosition, snapshot, props);

            wsHandler.broadcastSignal(action, snapshot);

            TradeNotification notification = new TradeNotification(
                    symbol, action, snapshot.closePrice(),
                    snapshot.rsi(), snapshot.macdHistogram(), Instant.now());
            notificationDispatcher.notifyAllSubscribers(symbol, notification);
        }
    }

    /**
     * 為特定用戶評估策略並執行交易（Phase 2：每用戶獨立評估）。
     * 使用用戶的自訂策略參數建立臨時 StrategyService 實例。
     */
    public void evaluateAndExecuteForUser(
            Long userId, String symbol,
            IndicatorSnapshot snapshot,
            TradingStrategyProperties userProps,
            StrategyService userStrategy) {

        var openPosition = positionRepo
                .findByUserIdAndSymbolAndStatus(userId, symbol, PositionStatus.OPEN)
                .orElse(null);

        TradeAction action = userStrategy.evaluate(snapshot, openPosition, Instant.now());

        if (action != TradeAction.HOLD) {
            log.info("[交易執行] userId={} {} 產生訊號: {} @ ${}",
                    userId, symbol, action, snapshot.closePrice());
            executeAction(userId, action, symbol, openPosition, snapshot, userProps);

            wsHandler.broadcastSignal(action, snapshot);

            TradeNotification notification = new TradeNotification(
                    symbol, action, snapshot.closePrice(),
                    snapshot.rsi(), snapshot.macdHistogram(), Instant.now());
            notificationDispatcher.notifyUser(userId, notification);
        }
    }

    private void executeAction(Long userId, TradeAction action, String symbol,
                               TradePosition openPosition, IndicatorSnapshot snapshot,
                               TradingStrategyProperties actionProps) {
        Instant now = Instant.now();

        switch (action) {
            case LONG_ENTRY, SHORT_ENTRY -> {
                if (openPosition == null) {
                    PositionDirection dir = action == TradeAction.LONG_ENTRY
                            ? PositionDirection.LONG : PositionDirection.SHORT;
                    BigDecimal capital = calculateAvailableCapital(userId, actionProps);
                    if (userId != null) {
                        positionService.openPositionForUser(
                                userId, symbol, dir, snapshot.closePrice(), now,
                                capital, snapshot, false, actionProps.risk().stopLossPct());
                    } else {
                        positionService.openPosition(
                                symbol, dir, snapshot.closePrice(), now,
                                capital, snapshot, false);
                    }
                }
            }
            case LONG_EXIT, SHORT_EXIT -> {
                if (openPosition != null) {
                    ExitReason reason = determineExitReason(snapshot, openPosition, now, actionProps);
                    positionService.closePosition(
                            openPosition, snapshot.closePrice(), now,
                            reason, snapshot);
                }
            }
            default -> { /* HOLD */ }
        }
    }

    private BigDecimal calculateAvailableCapital(Long userId, TradingStrategyProperties capitalProps) {
        List<TradePosition> closedPositions;
        if (userId != null) {
            closedPositions = positionRepo.findByUserIdAndBacktestOrderByEntryTimeAsc(userId, false);
        } else {
            closedPositions = positionRepo.findByBacktestOrderByEntryTimeAsc(false);
        }

        BigDecimal totalPnl = closedPositions.stream()
                .filter(p -> p.getRealizedPnl() != null)
                .map(TradePosition::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BigDecimal.valueOf(capitalProps.risk().initialCapital()).add(totalPnl);
    }

    private ExitReason determineExitReason(
            IndicatorSnapshot snap, TradePosition pos, Instant currentTime,
            TradingStrategyProperties exitProps) {

        boolean isLong = pos.getDirection() == PositionDirection.LONG;
        double currentPrice = snap.closePrice().doubleValue();
        double entryPrice = pos.getEntryPrice().doubleValue();
        double rsi = snap.rsi().doubleValue();

        double priceDelta = isLong
                ? (currentPrice - entryPrice) / entryPrice
                : (entryPrice - currentPrice) / entryPrice;

        if (priceDelta <= -exitProps.risk().stopLossPct()) return ExitReason.STOP_LOSS;

        long minutesHeld = Duration.between(pos.getEntryTime(), currentTime).toMinutes();
        long barsHeld = minutesHeld / intervalParams.barDurationMinutes();
        int maxHoldingBars = exitProps.risk().maxHoldingDays() * intervalParams.barsPerDay();
        if (barsHeld >= maxHoldingBars) return ExitReason.MAX_HOLDING_PERIOD;

        if (isLong && rsi > exitProps.rsi().longExitExtreme()) return ExitReason.RSI_EXTREME;
        if (!isLong && rsi < exitProps.rsi().shortExitExtreme()) return ExitReason.RSI_EXTREME;

        return ExitReason.SIGNAL_REVERSAL;
    }
}
