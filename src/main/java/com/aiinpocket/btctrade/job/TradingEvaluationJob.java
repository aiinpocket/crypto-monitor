package com.aiinpocket.btctrade.job;

import com.aiinpocket.btctrade.config.BinanceApiProperties;
import com.aiinpocket.btctrade.config.IntervalConfig.IntervalParams;
import com.aiinpocket.btctrade.config.TradingStrategyProperties;
import com.aiinpocket.btctrade.model.dto.IndicatorSnapshot;
import com.aiinpocket.btctrade.model.entity.Kline;
import com.aiinpocket.btctrade.model.entity.TradePosition;
import com.aiinpocket.btctrade.model.enums.*;
import com.aiinpocket.btctrade.repository.KlineRepository;
import com.aiinpocket.btctrade.repository.TradePositionRepository;
import com.aiinpocket.btctrade.service.*;
import com.aiinpocket.btctrade.websocket.TradeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradingEvaluationJob extends QuartzJobBean {

    private final KlineRepository klineRepo;
    private final TradePositionRepository positionRepo;
    private final BarSeriesFactory barSeriesFactory;
    private final TechnicalIndicatorService indicatorService;
    private final StrategyService strategyService;
    private final PositionService positionService;
    private final TradingStrategyProperties props;
    private final BinanceApiProperties apiProperties;
    private final IntervalParams intervalParams;
    private final TradeWebSocketHandler wsHandler;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        try {
            String symbol = apiProperties.defaultSymbol();

            List<Kline> klines = klineRepo
                    .findBySymbolAndIntervalTypeOrderByOpenTimeAsc(symbol, apiProperties.defaultInterval());

            if (klines.size() < props.strategy().emaLong() + 10) {
                log.warn("Not enough kline data for evaluation: {} bars", klines.size());
                return;
            }

            BarSeries series = barSeriesFactory.createFromKlines(klines, "live");
            int lastIndex = series.getBarCount() - 1;
            IndicatorSnapshot snapshot = indicatorService.computeAt(series, lastIndex);

            var openPosition = positionRepo
                    .findBySymbolAndStatus(symbol, PositionStatus.OPEN)
                    .orElse(null);

            TradeAction action = strategyService.evaluate(
                    snapshot, openPosition, Instant.now());

            if (action != TradeAction.HOLD) {
                log.info("Live trade signal: {} for {} at price {}",
                        action, symbol, snapshot.closePrice());

                executeAction(action, symbol, openPosition, snapshot);
                wsHandler.broadcastSignal(action, snapshot);
            } else {
                log.info("No signal for {}: RSI={}, MACD_HIST={}, EMA_SHORT={}, EMA_LONG={}",
                        symbol, snapshot.rsi(), snapshot.macdHistogram(),
                        snapshot.emaShort(), snapshot.emaLong());
            }

        } catch (Exception e) {
            log.error("Trading evaluation failed", e);
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
            default -> { /* HOLD */ }
        }
    }

    private BigDecimal calculateAvailableCapital() {
        List<TradePosition> closedPositions = positionRepo
                .findByBacktestOrderByEntryTimeAsc(false);

        BigDecimal totalPnl = closedPositions.stream()
                .filter(p -> p.getRealizedPnl() != null)
                .map(TradePosition::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BigDecimal.valueOf(props.risk().initialCapital()).add(totalPnl);
    }

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
