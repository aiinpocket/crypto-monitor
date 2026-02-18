package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.TradingStrategyProperties;
import com.aiinpocket.btctrade.model.dto.IndicatorSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class TechnicalIndicatorService {

    private final TradingStrategyProperties props;

    /**
     * 預建立的指標集合，持有 ta4j 指標實例的內部快取。
     * 在回測迴圈中重複使用同一個 IndicatorSet，讓 ta4j 的
     * 內建快取生效，避免 O(n²) 重複計算。
     */
    public record IndicatorSet(
            ClosePriceIndicator closePrice,
            EMAIndicator emaShort,
            EMAIndicator emaLong,
            RSIIndicator rsi,
            MACDIndicator macd,
            EMAIndicator macdSignal,
            ADXIndicator adx,
            HighestValueIndicator highestEntry,
            LowestValueIndicator lowestEntry,
            HighestValueIndicator highestExit,
            LowestValueIndicator lowestExit
    ) {}

    /**
     * 針對給定的 BarSeries 一次性建立所有指標計算器。
     * 回測時在迴圈外呼叫一次，之後在迴圈內用 {@link #computeFromSet} 取值。
     */
    public IndicatorSet createIndicators(BarSeries series) {
        var sp = props.strategy();
        var closePrice = new ClosePriceIndicator(series);
        var highPrice = new HighPriceIndicator(series);
        var lowPrice = new LowPriceIndicator(series);

        return new IndicatorSet(
                closePrice,
                new EMAIndicator(closePrice, sp.emaShort()),
                new EMAIndicator(closePrice, sp.emaLong()),
                new RSIIndicator(closePrice, sp.rsiPeriod()),
                new MACDIndicator(closePrice, sp.macdShort(), sp.macdLong()),
                new EMAIndicator(new MACDIndicator(closePrice, sp.macdShort(), sp.macdLong()), sp.macdSignal()),
                new ADXIndicator(series, sp.rsiPeriod()),
                new HighestValueIndicator(highPrice, sp.donchianEntry()),
                new LowestValueIndicator(lowPrice, sp.donchianEntry()),
                new HighestValueIndicator(highPrice, sp.donchianExit()),
                new LowestValueIndicator(lowPrice, sp.donchianExit())
        );
    }

    /**
     * 使用預建立的指標集合計算指定 index 的快照。
     * ta4j 指標內部有快取，連續遞增的 index 呼叫效率為 O(1)。
     */
    public IndicatorSnapshot computeFromSet(IndicatorSet set, BarSeries series, int index) {
        return buildSnapshot(
                set.closePrice, set.emaShort, set.emaLong, set.rsi,
                set.macd, set.macdSignal, set.adx,
                set.highestEntry, set.lowestEntry, set.highestExit, set.lowestExit,
                series, index);
    }

    /**
     * 即時用途（KlineClosedEventHandler）：每次呼叫建立新的指標實例。
     * 僅用於單次計算，不適合迴圈內使用。
     */
    public IndicatorSnapshot computeAt(BarSeries series, int index) {
        var sp = props.strategy();
        var closePrice = new ClosePriceIndicator(series);
        var highPrice = new HighPriceIndicator(series);
        var lowPrice = new LowPriceIndicator(series);

        var emaShort = new EMAIndicator(closePrice, sp.emaShort());
        var emaLong = new EMAIndicator(closePrice, sp.emaLong());
        var rsi = new RSIIndicator(closePrice, sp.rsiPeriod());
        var macd = new MACDIndicator(closePrice, sp.macdShort(), sp.macdLong());
        var macdSignal = new EMAIndicator(macd, sp.macdSignal());
        var adx = new ADXIndicator(series, sp.rsiPeriod());

        var highestEntry = new HighestValueIndicator(highPrice, sp.donchianEntry());
        var lowestEntry = new LowestValueIndicator(lowPrice, sp.donchianEntry());
        var highestExit = new HighestValueIndicator(highPrice, sp.donchianExit());
        var lowestExit = new LowestValueIndicator(lowPrice, sp.donchianExit());

        return buildSnapshot(closePrice, emaShort, emaLong, rsi, macd, macdSignal, adx,
                highestEntry, lowestEntry, highestExit, lowestExit, series, index);
    }

    private IndicatorSnapshot buildSnapshot(
            ClosePriceIndicator closePrice,
            EMAIndicator emaShort, EMAIndicator emaLong,
            RSIIndicator rsi, MACDIndicator macd, EMAIndicator macdSignal,
            ADXIndicator adx,
            HighestValueIndicator highestEntry, LowestValueIndicator lowestEntry,
            HighestValueIndicator highestExit, LowestValueIndicator lowestExit,
            BarSeries series, int index) {

        // EMA 交叉偵測
        boolean goldenCross = index > 0
                && emaShort.getValue(index - 1).isLessThan(emaLong.getValue(index - 1))
                && emaShort.getValue(index).isGreaterThanOrEqual(emaLong.getValue(index));

        boolean deathCross = index > 0
                && emaShort.getValue(index - 1).isGreaterThan(emaLong.getValue(index - 1))
                && emaShort.getValue(index).isLessThanOrEqual(emaLong.getValue(index));

        // EMA 趨勢方向
        boolean emaTrendBullish = emaShort.getValue(index).isGreaterThanOrEqual(emaLong.getValue(index));

        // MACD 柱狀圖
        Num macdHistCurr = macd.getValue(index).minus(macdSignal.getValue(index));
        Num macdHistPrev = index > 0
                ? macd.getValue(index - 1).minus(macdSignal.getValue(index - 1))
                : macdHistCurr;

        // MACD 柱狀圖零軸交叉
        boolean macdBullishCross = macdHistPrev.isLessThanOrEqual(series.numFactory().zero())
                && macdHistCurr.isGreaterThan(series.numFactory().zero());
        boolean macdBearishCross = macdHistPrev.isGreaterThanOrEqual(series.numFactory().zero())
                && macdHistCurr.isLessThan(series.numFactory().zero());

        // Donchian Channel — 使用前一根 K 線的通道值，避免前瞻偏差
        int prevIdx = Math.max(0, index - 1);
        BigDecimal donchianHigh = toBigDecimal(highestEntry.getValue(prevIdx));
        BigDecimal donchianLow = toBigDecimal(lowestEntry.getValue(prevIdx));
        BigDecimal donchianExitHigh = toBigDecimal(highestExit.getValue(prevIdx));
        BigDecimal donchianExitLow = toBigDecimal(lowestExit.getValue(prevIdx));

        return new IndicatorSnapshot(
                toBigDecimal(emaShort.getValue(index)),
                toBigDecimal(emaLong.getValue(index)),
                toBigDecimal(rsi.getValue(index)),
                toBigDecimal(macd.getValue(index)),
                toBigDecimal(macdSignal.getValue(index)),
                toBigDecimal(macdHistCurr),
                toBigDecimal(closePrice.getValue(index)),
                goldenCross,
                deathCross,
                emaTrendBullish,
                macdBullishCross,
                macdBearishCross,
                toBigDecimal(adx.getValue(index)),
                donchianHigh,
                donchianLow,
                donchianExitHigh,
                donchianExitLow
        );
    }

    private BigDecimal toBigDecimal(Num num) {
        return BigDecimal.valueOf(num.doubleValue()).setScale(8, RoundingMode.HALF_UP);
    }
}
