package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.IntervalConfig.IntervalParams;
import com.aiinpocket.btctrade.config.TradingStrategyProperties;
import com.aiinpocket.btctrade.model.dto.BacktestReport;
import com.aiinpocket.btctrade.model.dto.BacktestReport.EquityCurvePoint;
import com.aiinpocket.btctrade.model.dto.BacktestReport.TradeDetail;
import com.aiinpocket.btctrade.model.dto.BacktestResultWithUnrealized;
import com.aiinpocket.btctrade.model.dto.IndicatorSnapshot;
import com.aiinpocket.btctrade.model.entity.Kline;
import com.aiinpocket.btctrade.model.enums.PositionDirection;
import com.aiinpocket.btctrade.model.enums.TradeAction;
import com.aiinpocket.btctrade.repository.KlineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 回測引擎 V3 — 純記憶體模擬，零 DB 寫入，無前瞻偏差。
 * <p>
 * 核心改進：
 * 1. 停損檢查使用日內最高/最低價
 * 2. 移動停利：當浮盈超過啟動門檻時，自動上調停損至鎖定利潤的位置
 * 3. 移除 SIGNAL_REVERSAL 出場（EMA 交叉太頻繁，不適合做出場訊號）
 * 4. 時間單位泛化：支援任意 K 線間隔（5m, 1h, 1d 等）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestService {

    private final KlineRepository klineRepo;
    private final BarSeriesFactory barSeriesFactory;
    private final TechnicalIndicatorService indicatorService;
    private final StrategyService strategyService;
    private final TradingStrategyProperties props;
    private final IntervalParams intervalParams;

    // ======== 回測內部記憶體結構 ========
    private static final class OpenPos {
        final PositionDirection direction;
        final BigDecimal entryPrice;
        final Instant entryTime;
        final BigDecimal quantity;
        final BigDecimal capitalUsed;
        final int entryBarIndex;
        BigDecimal stopLossPrice;
        BigDecimal peakPnlPct; // 追蹤最高浮盈百分比

        OpenPos(PositionDirection direction, BigDecimal entryPrice, Instant entryTime,
                BigDecimal quantity, BigDecimal capitalUsed, BigDecimal stopLossPrice, int entryBarIndex) {
            this.direction = direction;
            this.entryPrice = entryPrice;
            this.entryTime = entryTime;
            this.quantity = quantity;
            this.capitalUsed = capitalUsed;
            this.stopLossPrice = stopLossPrice;
            this.entryBarIndex = entryBarIndex;
            this.peakPnlPct = BigDecimal.ZERO;
        }
    }

    /**
     * 使用系統全域配置執行回測（原有方法，向下相容）。
     * 委託給 {@link #runBacktestWithParams} 使用注入的全域參數。
     */
    public BacktestReport runBacktest(String symbol, Instant startDate, Instant endDate) {
        return runBacktestWithParams(symbol, startDate, endDate, props);
    }

    /**
     * 使用自訂策略參數執行回測。
     * 建立臨時的 TechnicalIndicatorService 和 StrategyService 實例，
     * 以用戶指定的 {@code customProps} 取代全域注入的參數。
     *
     * <p>此方法是用戶回測的核心入口，由 UserBacktestService 在 backtestExecutor 執行緒池中呼叫。
     * TechnicalIndicatorService 和 StrategyService 都是無狀態的，
     * 可以安全地以 new 建立臨時實例而不影響系統正常運作的全域服務。
     *
     * @param symbol      交易對符號
     * @param startDate   回測起始時間
     * @param endDate     回測結束時間
     * @param customProps 自訂策略參數（從 StrategyTemplate.toProperties() 取得）
     * @return 完整的回測報告
     */
    public BacktestReport runBacktestWithParams(
            String symbol, Instant startDate, Instant endDate,
            TradingStrategyProperties customProps) {

        // 建立使用自訂參數的臨時服務實例（無狀態，線程安全）
        TechnicalIndicatorService customIndicator = new TechnicalIndicatorService(customProps);
        StrategyService customStrategy = new StrategyService(customProps, intervalParams);

        return executeBacktest(symbol, startDate, endDate, customProps, customIndicator, customStrategy, true).report();
    }

    /**
     * 績效計算專用回測入口。
     * 回測結束時不強制平倉，改為回報未平倉部位的浮盈方向和百分比。
     *
     * @param symbol      交易對符號
     * @param startDate   回測起始時間
     * @param endDate     回測結束時間
     * @param customProps 自訂策略參數
     * @return 含未實現損益的回測結果
     */
    public BacktestResultWithUnrealized runBacktestForPerformance(
            String symbol, Instant startDate, Instant endDate,
            TradingStrategyProperties customProps) {

        TechnicalIndicatorService customIndicator = new TechnicalIndicatorService(customProps);
        StrategyService customStrategy = new StrategyService(customProps, intervalParams);

        return executeBacktest(symbol, startDate, endDate, customProps, customIndicator, customStrategy, false);
    }

    /**
     * 回測引擎核心邏輯。
     * 從資料庫載入 K 線資料，逐根模擬交易策略的進出場決策。
     * 純記憶體運算，零 DB 寫入，無前瞻偏差。
     *
     * @param forceCloseAtEnd true=結束時強制平倉（標準回測），false=保留未平倉部位（績效計算用）
     */
    private BacktestResultWithUnrealized executeBacktest(
            String symbol, Instant startDate, Instant endDate,
            TradingStrategyProperties backtestProps,
            TechnicalIndicatorService backtestIndicator,
            StrategyService backtestStrategy,
            boolean forceCloseAtEnd) {

        List<Kline> klines = klineRepo
                .findBySymbolAndIntervalTypeAndOpenTimeBetweenOrderByOpenTimeAsc(
                        symbol, intervalParams.interval(), startDate, endDate);

        log.info("[回測] {} 載入 {} 根 K 線 ({} → {})", symbol, klines.size(), startDate, endDate);

        int minBars = backtestProps.strategy().emaLong() + 10;
        if (klines.size() < minBars) {
            throw new IllegalStateException("需要至少 " + minBars + " 根 K 線，目前只有 " + klines.size());
        }

        // 提取回測所需的輕量資料，釋放 Kline entity 佔用的記憶體
        record BarData(Instant openTime, BigDecimal open, BigDecimal high,
                       BigDecimal low, BigDecimal close, BigDecimal volume) {}
        List<BarData> bars = klines.stream()
                .map(k -> new BarData(k.getOpenTime(), k.getOpenPrice(), k.getHighPrice(),
                        k.getLowPrice(), k.getClosePrice(), k.getVolume()))
                .toList();

        BarSeries series = barSeriesFactory.createFromKlines(klines, "backtest");
        klines = null; // 允許 GC 回收原始 Kline entities

        // ---- 預建立指標集合（迴圈外一次性建立，讓 ta4j 內部快取生效）----
        TechnicalIndicatorService.IndicatorSet indicatorSet = backtestIndicator.createIndicators(series);

        // ---- 模擬狀態（純記憶體）----
        BigDecimal capital = BigDecimal.valueOf(backtestProps.risk().initialCapital());
        final BigDecimal initialCapital = capital;
        OpenPos openPos = null;
        List<TradeDetail> trades = new ArrayList<>();
        List<EquityCurvePoint> equityCurve = new ArrayList<>();
        int tradeNumber = 0;

        // 權益曲線降採樣：超過 2000 點時只取樣
        int totalBarsToProcess = series.getBarCount() - Math.max(backtestProps.strategy().emaLong(), 35);
        int equitySampleStep = Math.max(1, totalBarsToProcess / 2000);

        int warmup = Math.max(backtestProps.strategy().emaLong(), 35);

        for (int i = warmup; i < series.getBarCount(); i++) {
            BarData bar = bars.get(i);
            Instant barTime = bar.openTime();

            // ====== 步驟 1：更新移動停利 + 日內停損檢查 ======
            if (openPos != null) {
                updateTrailingStop(openPos, bar.high(), bar.low(), backtestProps);

                BigDecimal worstPrice = intrabarStopCheck(openPos, bar.high(), bar.low());
                if (worstPrice != null) {
                    BigDecimal exitPrice = openPos.stopLossPrice;
                    BigDecimal pnl = calcPnl(openPos, exitPrice);
                    capital = capital.add(openPos.capitalUsed).add(pnl);
                    tradeNumber++;
                    String reason = pnl.compareTo(BigDecimal.ZERO) >= 0 ? "TRAILING_STOP" : "STOP_LOSS";
                    trades.add(buildTradeDetail(tradeNumber, openPos, exitPrice, barTime, pnl, reason, i));
                    openPos = null;
                }
            }

            // ====== 步驟 2：計算指標（使用預建立的指標集合，O(1) 查詢）======
            IndicatorSnapshot snap = backtestIndicator.computeFromSet(indicatorSet, series, i);

            // ====== 步驟 3：持倉中 → 檢查其他出場條件 ======
            if (openPos != null) {
                String exitReason = checkExitConditions(snap, openPos, barTime, i, backtestProps);
                if (exitReason != null) {
                    BigDecimal exitPrice = snap.closePrice();
                    BigDecimal pnl = calcPnl(openPos, exitPrice);
                    capital = capital.add(openPos.capitalUsed).add(pnl);
                    tradeNumber++;
                    trades.add(buildTradeDetail(tradeNumber, openPos, exitPrice, barTime, pnl, exitReason, i));
                    openPos = null;
                }
            }

            // ====== 步驟 4：無持倉 → 檢查進場（使用自訂策略服務）======
            if (openPos == null && capital.compareTo(BigDecimal.ZERO) > 0) {
                TradeAction action = backtestStrategy.evaluate(snap, null, barTime);
                if (action == TradeAction.LONG_ENTRY || action == TradeAction.SHORT_ENTRY) {
                    PositionDirection dir = action == TradeAction.LONG_ENTRY
                            ? PositionDirection.LONG : PositionDirection.SHORT;
                    BigDecimal price = snap.closePrice();
                    // 按倉位比例計算進場資金（positionSizePct=1.0 為全倉，0.5 為半倉）
                    double posPct = Math.max(0.01, Math.min(1.0, backtestProps.risk().positionSizePct()));
                    BigDecimal positionCapital = capital.multiply(BigDecimal.valueOf(posPct))
                            .setScale(2, RoundingMode.HALF_DOWN);
                    BigDecimal qty = positionCapital.divide(price, 8, RoundingMode.HALF_DOWN);
                    double slPct = backtestProps.risk().stopLossPct();
                    BigDecimal slPrice = dir == PositionDirection.LONG
                            ? price.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(slPct)))
                            : price.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(slPct)));

                    openPos = new OpenPos(dir, price, barTime, qty, positionCapital,
                            slPrice.setScale(8, RoundingMode.HALF_UP), i);
                    capital = capital.subtract(positionCapital);
                }
            }

            // ====== 權益曲線（降採樣）======
            // equity = 保留資金 + 持倉資金 + 浮動損益
            BigDecimal equity = capital;
            if (openPos != null) {
                equity = equity.add(openPos.capitalUsed).add(calcPnl(openPos, bar.close()));
            }
            int barOffset = i - warmup;
            // 始終保存交易發生的 bar + 按步長取樣 + 最後一根
            boolean isTradeBoundary = (openPos == null && barOffset > 0) || barOffset == 0;
            if (barOffset % equitySampleStep == 0 || isTradeBoundary || i == series.getBarCount() - 1) {
                equityCurve.add(new EquityCurvePoint(barTime, equity));
            }
        }

        // 結束時處理未平倉部位
        BigDecimal unrealizedPnlPct = null;
        String unrealizedDirection = null;

        BarData lastBar = bars.getLast();
        if (openPos != null && forceCloseAtEnd) {
            // 標準回測：強制平倉
            BigDecimal lastClose = lastBar.close();
            BigDecimal pnl = calcPnl(openPos, lastClose);
            capital = capital.add(openPos.capitalUsed).add(pnl);
            tradeNumber++;
            trades.add(buildTradeDetail(tradeNumber, openPos, lastClose,
                    lastBar.openTime(), pnl, "END_OF_BACKTEST", series.getBarCount() - 1));
        } else if (openPos != null) {
            // 績效計算模式：計算未實現損益，不加入 trades
            BigDecimal lastClose = lastBar.close();
            BigDecimal pnl = calcPnl(openPos, lastClose);
            unrealizedPnlPct = pnl.divide(openPos.capitalUsed, 6, RoundingMode.HALF_UP);
            unrealizedDirection = openPos.direction.name();
            // finalCapital = 保留資金 + 已投入資金（不含未平倉 PnL）
            capital = capital.add(openPos.capitalUsed);
        }

        BacktestReport report = buildReport(symbol, startDate, endDate, series.getBarCount(),
                trades, equityCurve, initialCapital, capital);
        return new BacktestResultWithUnrealized(report, unrealizedPnlPct, unrealizedDirection);
    }

    /**
     * 移動停利邏輯：
     * 當浮盈百分比超過啟動門檻（trailingActivatePct）時，
     * 將停損價上調至 (entryPrice + offset)，鎖定部分利潤。
     * 持續追蹤最高浮盈，動態上調停損。
     */
    private void updateTrailingStop(OpenPos pos, BigDecimal highPrice, BigDecimal lowPrice,
                                    TradingStrategyProperties backtestProps) {
        var risk = backtestProps.risk();
        double activatePct = risk.trailingActivatePct();
        double offsetPct = risk.trailingOffsetPct();

        // 計算當根 K 線中的最有利價格
        BigDecimal bestPrice = pos.direction == PositionDirection.LONG
                ? highPrice : lowPrice;

        // 計算當前浮盈百分比
        double pnlPct = pos.direction == PositionDirection.LONG
                ? bestPrice.subtract(pos.entryPrice).doubleValue() / pos.entryPrice.doubleValue()
                : pos.entryPrice.subtract(bestPrice).doubleValue() / pos.entryPrice.doubleValue();

        // 更新峰值浮盈
        if (pnlPct > pos.peakPnlPct.doubleValue()) {
            pos.peakPnlPct = BigDecimal.valueOf(pnlPct);
        }

        // 當浮盈超過啟動門檻時，上調停損
        if (pos.peakPnlPct.doubleValue() >= activatePct) {
            // 新停損 = 進場價 × (1 + (峰值浮盈 - offset))
            double trailLevel = pos.peakPnlPct.doubleValue() - offsetPct;
            if (trailLevel < 0) trailLevel = 0; // 至少保本

            BigDecimal newStopPrice;
            if (pos.direction == PositionDirection.LONG) {
                newStopPrice = pos.entryPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(trailLevel)));
            } else {
                newStopPrice = pos.entryPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(trailLevel)));
            }
            newStopPrice = newStopPrice.setScale(8, RoundingMode.HALF_UP);

            // 只允許停損單方向移動（多頭只能上調，空頭只能下調）
            if (pos.direction == PositionDirection.LONG && newStopPrice.compareTo(pos.stopLossPrice) > 0) {
                pos.stopLossPrice = newStopPrice;
            } else if (pos.direction == PositionDirection.SHORT && newStopPrice.compareTo(pos.stopLossPrice) < 0) {
                pos.stopLossPrice = newStopPrice;
            }
        }
    }

    // ---- 日內停損檢查：用最高/最低價判斷是否觸及停損 ----
    private BigDecimal intrabarStopCheck(OpenPos pos, BigDecimal highPrice, BigDecimal lowPrice) {
        if (pos.direction == PositionDirection.LONG) {
            if (lowPrice.compareTo(pos.stopLossPrice) <= 0) {
                return pos.stopLossPrice;
            }
        } else {
            if (highPrice.compareTo(pos.stopLossPrice) >= 0) {
                return pos.stopLossPrice;
            }
        }
        return null;
    }

    // ---- 其他出場條件（停損/停利已在步驟 1 處理）----
    private String checkExitConditions(IndicatorSnapshot snap, OpenPos pos, Instant barTime, int currentBarIndex,
                                       TradingStrategyProperties backtestProps) {
        var riskParams = backtestProps.risk();
        var rsiParams = backtestProps.rsi();
        boolean isLong = pos.direction == PositionDirection.LONG;
        double rsi = snap.rsi().doubleValue();

        int barsHeld = currentBarIndex - pos.entryBarIndex;

        // 最長持倉（以 bar 為單位）
        int maxHoldingBars = riskParams.maxHoldingDays() * intervalParams.barsPerDay();
        if (barsHeld >= maxHoldingBars) return "MAX_HOLDING";

        // RSI 極端
        if (isLong && rsi > rsiParams.longExitExtreme()) return "RSI_EXTREME";
        if (!isLong && rsi < rsiParams.shortExitExtreme()) return "RSI_EXTREME";

        // 時間止損：持倉超過 N bars 仍虧損 → 出場
        int timeStopBars = riskParams.timeStopDays() * intervalParams.barsPerDay();
        if (timeStopBars > 0 && barsHeld >= timeStopBars) {
            BigDecimal unrealizedPnl = calcPnl(pos, snap.closePrice());
            if (unrealizedPnl.compareTo(BigDecimal.ZERO) < 0) {
                return "TIME_STOP";
            }
        }

        return null;
    }

    private BigDecimal calcPnl(OpenPos pos, BigDecimal exitPrice) {
        return pos.direction == PositionDirection.LONG
                ? exitPrice.subtract(pos.entryPrice).multiply(pos.quantity)
                : pos.entryPrice.subtract(exitPrice).multiply(pos.quantity);
    }

    private TradeDetail buildTradeDetail(int num, OpenPos pos, BigDecimal exitPrice,
                                         Instant exitTime, BigDecimal pnl, String reason, int exitBarIndex) {
        BigDecimal returnPct = pnl.divide(pos.capitalUsed, 6, RoundingMode.HALF_UP);
        int holdBars = exitBarIndex - pos.entryBarIndex;
        return new TradeDetail(num, pos.direction.name(), pos.entryTime, exitTime,
                pos.entryPrice, exitPrice, pnl.setScale(2, RoundingMode.HALF_UP),
                returnPct, reason, holdBars);
    }

    // ======== 績效報告 ========
    private BacktestReport buildReport(
            String symbol, Instant startDate, Instant endDate, int totalBars,
            List<TradeDetail> trades, List<EquityCurvePoint> equityCurve,
            BigDecimal initialCapital, BigDecimal finalCapital) {

        int total = trades.size();
        int wins = (int) trades.stream().filter(t -> t.pnl().compareTo(BigDecimal.ZERO) > 0).count();
        int losses = (int) trades.stream().filter(t -> t.pnl().compareTo(BigDecimal.ZERO) < 0).count();
        BigDecimal winRate = total > 0
                ? BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal totalReturn = finalCapital.subtract(initialCapital)
                .divide(initialCapital, 6, RoundingMode.HALF_UP);

        // 年化報酬用日曆天計算（不受 bar interval 影響）
        long totalDays = Duration.between(startDate, endDate).toDays();
        BigDecimal annualizedReturn = BigDecimal.ZERO;
        if (totalDays > 0 && finalCapital.compareTo(BigDecimal.ZERO) > 0) {
            double ratio = finalCapital.doubleValue() / initialCapital.doubleValue();
            annualizedReturn = BigDecimal.valueOf(Math.pow(ratio, 365.0 / totalDays) - 1)
                    .setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal maxDrawdown = calcMaxDrawdown(equityCurve);
        BigDecimal sharpeRatio = calcSharpe(equityCurve);

        BigDecimal totalWins = trades.stream().map(TradeDetail::pnl)
                .filter(p -> p.compareTo(BigDecimal.ZERO) > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLosses = trades.stream().map(TradeDetail::pnl)
                .filter(p -> p.compareTo(BigDecimal.ZERO) < 0).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profitFactor = totalLosses.compareTo(BigDecimal.ZERO) > 0
                ? totalWins.divide(totalLosses, 4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(999);

        BigDecimal avgWin = wins > 0 ? totalWins.divide(BigDecimal.valueOf(wins), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgLoss = losses > 0 ? totalLosses.divide(BigDecimal.valueOf(losses), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        BigDecimal maxSingleLossPct = trades.stream()
                .map(TradeDetail::returnPct)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        int maxConsecLoss = calcMaxConsecLoss(trades);

        boolean passed = annualizedReturn.doubleValue() >= 0.30
                && maxSingleLossPct.doubleValue() >= -0.10
                && maxDrawdown.abs().doubleValue() <= 0.30;

        log.info("══════════════ Backtest Report ══════════════");
        log.info("Period:      {} → {}", startDate, endDate);
        log.info("Interval:    {}", intervalParams.interval());
        log.info("Trades:      {} (W:{} L:{}), WinRate: {}%", total, wins, losses, winRate.multiply(BigDecimal.valueOf(100)));
        log.info("Return:      Total {}%, Annualized {}%", totalReturn.multiply(BigDecimal.valueOf(100)), annualizedReturn.multiply(BigDecimal.valueOf(100)));
        log.info("Risk:        MaxDD {}%, MaxSingleLoss {}%, Sharpe {}", maxDrawdown.multiply(BigDecimal.valueOf(100)), maxSingleLossPct.multiply(BigDecimal.valueOf(100)), sharpeRatio);
        log.info("Capital:     {} → {}", initialCapital, finalCapital.setScale(2, RoundingMode.HALF_UP));
        log.info("ProfitFactor:{}, AvgWin: {}, AvgLoss: {}", profitFactor, avgWin, avgLoss);
        log.info("PASSED:      {}", passed);
        log.info("══════════════════════════════════════════════");

        return new BacktestReport(symbol, startDate, endDate, totalBars,
                total, wins, losses, winRate,
                totalReturn, annualizedReturn, maxDrawdown,
                sharpeRatio, profitFactor, avgWin, avgLoss, maxConsecLoss,
                initialCapital, finalCapital.setScale(2, RoundingMode.HALF_UP),
                trades, equityCurve, passed);
    }

    private BigDecimal calcMaxDrawdown(List<EquityCurvePoint> curve) {
        if (curve.isEmpty()) return BigDecimal.ZERO;
        BigDecimal peak = curve.getFirst().equity();
        BigDecimal maxDD = BigDecimal.ZERO;
        for (var p : curve) {
            if (p.equity().compareTo(peak) > 0) peak = p.equity();
            BigDecimal dd = p.equity().subtract(peak).divide(peak, 6, RoundingMode.HALF_UP);
            if (dd.compareTo(maxDD) < 0) maxDD = dd;
        }
        return maxDD.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calcSharpe(List<EquityCurvePoint> curve) {
        if (curve.size() < 2) return BigDecimal.ZERO;
        List<Double> rets = new ArrayList<>();
        for (int i = 1; i < curve.size(); i++) {
            double prev = curve.get(i - 1).equity().doubleValue();
            double curr = curve.get(i).equity().doubleValue();
            if (prev > 0) rets.add((curr - prev) / prev);
        }
        if (rets.isEmpty()) return BigDecimal.ZERO;
        double avg = rets.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = rets.stream().mapToDouble(r -> Math.pow(r - avg, 2)).average().orElse(0);
        double std = Math.sqrt(var);
        if (std == 0) return BigDecimal.ZERO;
        double riskFreePerBar = 0.04 / intervalParams.barsPerYear();
        return BigDecimal.valueOf((avg - riskFreePerBar) / std * intervalParams.sharpeAnnualizer())
                .setScale(4, RoundingMode.HALF_UP);
    }

    private int calcMaxConsecLoss(List<TradeDetail> trades) {
        int max = 0, cur = 0;
        for (var t : trades) {
            if (t.pnl().compareTo(BigDecimal.ZERO) < 0) { cur++; max = Math.max(max, cur); }
            else cur = 0;
        }
        return max;
    }
}
