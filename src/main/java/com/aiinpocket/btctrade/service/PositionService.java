package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.TradingStrategyProperties;
import com.aiinpocket.btctrade.model.dto.IndicatorSnapshot;
import com.aiinpocket.btctrade.model.entity.TradePosition;
import com.aiinpocket.btctrade.model.entity.TradeSignal;
import com.aiinpocket.btctrade.model.enums.*;
import com.aiinpocket.btctrade.repository.TradePositionRepository;
import com.aiinpocket.btctrade.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionService {

    private final TradePositionRepository positionRepo;
    private final TradeSignalRepository signalRepo;
    private final TradingStrategyProperties props;
    private final BattleService battleService;

    @Transactional
    public TradePosition openPosition(
            String symbol, PositionDirection direction,
            BigDecimal price, Instant time,
            BigDecimal availableCapital,
            IndicatorSnapshot snapshot,
            boolean isBacktest) {
        return openPosition(null, symbol, direction, price, time, availableCapital, snapshot, isBacktest, props.risk().stopLossPct());
    }

    /** 為特定用戶開倉（使用用戶自訂停損比例） */
    @Transactional
    public TradePosition openPositionForUser(
            Long userId, String symbol, PositionDirection direction,
            BigDecimal price, Instant time,
            BigDecimal availableCapital,
            IndicatorSnapshot snapshot,
            boolean isBacktest, double stopLossPct) {
        return openPosition(userId, symbol, direction, price, time, availableCapital, snapshot, isBacktest, stopLossPct);
    }

    private TradePosition openPosition(
            Long userId, String symbol, PositionDirection direction,
            BigDecimal price, Instant time,
            BigDecimal availableCapital,
            IndicatorSnapshot snapshot,
            boolean isBacktest, double slPct) {

        BigDecimal quantity = availableCapital.divide(price, 8, RoundingMode.HALF_DOWN);

        BigDecimal stopLoss = direction == PositionDirection.LONG
                ? price.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(slPct)))
                : price.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(slPct)));

        TradePosition position = TradePosition.builder()
                .userId(userId)
                .symbol(symbol)
                .direction(direction)
                .status(PositionStatus.OPEN)
                .entryPrice(price)
                .entryTime(time)
                .quantity(quantity)
                .capitalUsed(availableCapital)
                .stopLossPrice(stopLoss.setScale(8, RoundingMode.HALF_UP))
                .backtest(isBacktest)
                .build();

        positionRepo.save(position);

        saveSignal(userId, symbol, time, snapshot,
                direction == PositionDirection.LONG
                        ? TradeAction.LONG_ENTRY : TradeAction.SHORT_ENTRY,
                isBacktest);

        log.info("Opened {} position: price={}, qty={}, stopLoss={}",
                direction, price, quantity, stopLoss);

        // 非回測交易觸發怪物遭遇（遊戲化）
        if (!isBacktest) {
            try {
                battleService.startEncounters(symbol, slPct, time,
                        direction.name(), price);
            } catch (Exception e) {
                log.warn("[戰鬥] 觸發遭遇失敗，不影響交易: {}", e.getMessage());
            }
        }

        return position;
    }

    @Transactional
    public TradePosition closePosition(
            TradePosition position, BigDecimal exitPrice,
            Instant exitTime, ExitReason reason,
            IndicatorSnapshot snapshot) {

        boolean isLong = position.getDirection() == PositionDirection.LONG;

        BigDecimal pnl = isLong
                ? exitPrice.subtract(position.getEntryPrice()).multiply(position.getQuantity())
                : position.getEntryPrice().subtract(exitPrice).multiply(position.getQuantity());

        BigDecimal returnPct = pnl.divide(
                position.getCapitalUsed(), 4, RoundingMode.HALF_UP);

        position.setExitPrice(exitPrice);
        position.setExitTime(exitTime);
        position.setRealizedPnl(pnl.setScale(2, RoundingMode.HALF_UP));
        position.setReturnPct(returnPct);
        position.setExitReason(reason);
        position.setStatus(mapReasonToStatus(reason));

        positionRepo.save(position);

        saveSignal(position.getUserId(), position.getSymbol(), exitTime, snapshot,
                isLong ? TradeAction.LONG_EXIT : TradeAction.SHORT_EXIT,
                position.isBacktest());

        log.info("Closed {} position: exitPrice={}, PnL={}, return={}%",
                position.getDirection(), exitPrice, pnl,
                returnPct.multiply(BigDecimal.valueOf(100)));

        // 非回測交易結算怪物遭遇（遊戲化）
        if (!position.isBacktest()) {
            try {
                battleService.resolveEncounters(position.getSymbol(), returnPct,
                        exitTime, exitPrice);
            } catch (Exception e) {
                log.warn("[戰鬥] 結算遭遇失敗，不影響交易: {}", e.getMessage());
            }
        }

        return position;
    }

    private void saveSignal(Long userId, String symbol, Instant time,
                            IndicatorSnapshot snapshot, TradeAction action,
                            boolean isBacktest) {
        TradeSignal signal = TradeSignal.builder()
                .userId(userId)
                .symbol(symbol)
                .signalTime(time)
                .action(action)
                .closePrice(snapshot.closePrice())
                .emaShort(snapshot.emaShort())
                .emaLong(snapshot.emaLong())
                .rsiValue(snapshot.rsi())
                .macdValue(snapshot.macdValue())
                .macdSignalValue(snapshot.macdSignal())
                .macdHistogram(snapshot.macdHistogram())
                .backtest(isBacktest)
                .build();
        signalRepo.save(signal);
    }

    private PositionStatus mapReasonToStatus(ExitReason reason) {
        return switch (reason) {
            case SIGNAL_REVERSAL -> PositionStatus.CLOSED_BY_SIGNAL;
            case STOP_LOSS -> PositionStatus.CLOSED_BY_STOP_LOSS;
            case RSI_EXTREME -> PositionStatus.CLOSED_BY_RSI_EXTREME;
            case MAX_HOLDING_PERIOD -> PositionStatus.CLOSED_BY_MAX_HOLDING;
        };
    }
}
