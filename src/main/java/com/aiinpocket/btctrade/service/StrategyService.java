package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.IntervalConfig.IntervalParams;
import com.aiinpocket.btctrade.config.TradingStrategyProperties;
import com.aiinpocket.btctrade.model.dto.IndicatorSnapshot;
import com.aiinpocket.btctrade.model.entity.TradePosition;
import com.aiinpocket.btctrade.model.enums.PositionDirection;
import com.aiinpocket.btctrade.model.enums.TradeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 策略引擎 V5：趨勢跟隨 + 動量確認。
 * <p>
 * 進場觸發：MACD 柱狀圖零軸交叉（主要訊號）或 EMA 交叉（輔助訊號）。
 * 趨勢確認：EMA 短線與長線的相對位置。
 * 過濾器：ADX > 20 趨勢強度 + RSI 避免追高殺低。
 * 出場：停損 + 移動停利 + 時間止損 + RSI 極值 + 最長持倉。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyService {

    private final TradingStrategyProperties props;
    private final IntervalParams intervalParams;

    public TradeAction evaluate(
            IndicatorSnapshot snapshot,
            TradePosition openPosition,
            Instant currentTime) {

        if (openPosition != null) {
            return evaluateExit(snapshot, openPosition, currentTime);
        }
        return evaluateEntry(snapshot);
    }

    private TradeAction evaluateEntry(IndicatorSnapshot snap) {
        var rsiParams = props.rsi();
        double rsi = snap.rsi().doubleValue();
        double adx = snap.adx().doubleValue();

        // ADX 趨勢強度過濾器
        if (adx < 20) {
            return TradeAction.HOLD;
        }

        // ======== 做多條件 ========
        boolean longTrigger = snap.macdBullishCross() || snap.emaGoldenCross();
        boolean longTrend = snap.emaTrendBullish();
        boolean longRsi = rsi >= rsiParams.longEntryMin() && rsi <= rsiParams.longEntryMax();

        if (longTrigger && longTrend && longRsi) {
            log.debug("LONG_ENTRY: RSI={}, ADX={}, MACD_HIST={}", rsi, adx, snap.macdHistogram());
            return TradeAction.LONG_ENTRY;
        }

        // ======== 做空條件 ========
        boolean shortTrigger = snap.macdBearishCross() || snap.emaDeathCross();
        boolean shortTrend = !snap.emaTrendBullish();
        boolean shortRsi = rsi >= rsiParams.shortEntryMin() && rsi <= rsiParams.shortEntryMax();

        if (shortTrigger && shortTrend && shortRsi) {
            log.debug("SHORT_ENTRY: RSI={}, ADX={}, MACD_HIST={}", rsi, adx, snap.macdHistogram());
            return TradeAction.SHORT_ENTRY;
        }

        return TradeAction.HOLD;
    }

    private TradeAction evaluateExit(
            IndicatorSnapshot snap,
            TradePosition pos,
            Instant currentTime) {

        var riskParams = props.risk();
        var rsiParams = props.rsi();
        double currentPrice = snap.closePrice().doubleValue();
        double entryPrice = pos.getEntryPrice().doubleValue();
        double rsi = snap.rsi().doubleValue();
        boolean isLong = pos.getDirection() == PositionDirection.LONG;

        // 1. 停損檢查
        double priceDelta = isLong
                ? (currentPrice - entryPrice) / entryPrice
                : (entryPrice - currentPrice) / entryPrice;

        if (priceDelta <= -riskParams.stopLossPct()) {
            return isLong ? TradeAction.LONG_EXIT : TradeAction.SHORT_EXIT;
        }

        // 2. 最長持倉（以分鐘計算，轉換為 bar 數量）
        long minutesHeld = Duration.between(pos.getEntryTime(), currentTime).toMinutes();
        long barsHeld = minutesHeld / intervalParams.barDurationMinutes();
        int maxHoldingBars = riskParams.maxHoldingDays() * intervalParams.barsPerDay();
        if (barsHeld >= maxHoldingBars) {
            return isLong ? TradeAction.LONG_EXIT : TradeAction.SHORT_EXIT;
        }

        // 3. RSI 極端值
        if (isLong && rsi > rsiParams.longExitExtreme()) {
            return TradeAction.LONG_EXIT;
        }
        if (!isLong && rsi < rsiParams.shortExitExtreme()) {
            return TradeAction.SHORT_EXIT;
        }

        return TradeAction.HOLD;
    }
}
