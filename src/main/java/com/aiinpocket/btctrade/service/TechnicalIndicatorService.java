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

    public IndicatorSnapshot computeAt(BarSeries series, int index) {
        var sp = props.strategy();
        var closePrice = new ClosePriceIndicator(series);

        var emaShort = new EMAIndicator(closePrice, sp.emaShort());
        var emaLong = new EMAIndicator(closePrice, sp.emaLong());
        var rsi = new RSIIndicator(closePrice, sp.rsiPeriod());
        var macd = new MACDIndicator(closePrice, sp.macdShort(), sp.macdLong());
        var macdSignal = new EMAIndicator(macd, sp.macdSignal());
        var adx = new ADXIndicator(series, sp.rsiPeriod());

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

        // ======== Donchian Channel ========
        // 使用前一根 K 線的通道值，避免前瞻偏差
        var highPrice = new HighPriceIndicator(series);
        var lowPrice = new LowPriceIndicator(series);

        var highestEntry = new HighestValueIndicator(highPrice, sp.donchianEntry());
        var lowestEntry = new LowestValueIndicator(lowPrice, sp.donchianEntry());
        var highestExit = new HighestValueIndicator(highPrice, sp.donchianExit());
        var lowestExit = new LowestValueIndicator(lowPrice, sp.donchianExit());

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
