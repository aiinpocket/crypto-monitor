package com.aiinpocket.btctrade.model.entity;

import com.aiinpocket.btctrade.config.TradingStrategyProperties;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

/**
 * 策略模板 Entity。
 * 儲存完整的策略參數組合（技術指標 + 風控 + RSI），供用戶選擇和回測使用。
 *
 * <p>每個模板包含 {@link TradingStrategyProperties} 的所有子參數：
 * <ul>
 *   <li>技術指標參數：EMA 短/長期、RSI 週期、MACD 快/慢/信號線、唐奇安通道</li>
 *   <li>風控參數：停損比例、最大持倉天數、初始資金、移動停利等</li>
 *   <li>RSI 進出場參數：多空方向的進場範圍和極端出場值</li>
 * </ul>
 *
 * <p>用戶可以：
 * <ul>
 *   <li>使用系統預設模板（{@code systemDefault=true}，不可修改）</li>
 *   <li>從系統預設克隆一份自訂模板，修改參數後進行回測</li>
 * </ul>
 *
 * <p>提供 {@link #toProperties()} 方法將 Entity 轉換為 {@link TradingStrategyProperties}，
 * 供 BacktestService 直接使用。
 */
@Entity
@Table(name = "strategy_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬用戶（null 表示系統預設模板） */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    /** 模板名稱（例如 "系統預設 5m 策略"、"保守停損策略"） */
    @Column(nullable = false)
    private String name;

    /** 模板描述 */
    @Column(length = 500)
    private String description;

    /** 是否為系統預設模板（全域共用，不可被用戶修改） */
    @Column(nullable = false)
    private boolean systemDefault;

    // ======== 技術指標參數 ========

    /** EMA 短期週期（預設 12） */
    @Column(nullable = false)
    private int emaShort;

    /** EMA 長期週期（預設 26） */
    @Column(nullable = false)
    private int emaLong;

    /** RSI 計算週期（預設 14） */
    @Column(nullable = false)
    private int rsiPeriod;

    /** MACD 快線週期（預設 12） */
    @Column(nullable = false)
    private int macdShort;

    /** MACD 慢線週期（預設 26） */
    @Column(nullable = false)
    private int macdLong;

    /** MACD 信號線週期（預設 9） */
    @Column(nullable = false)
    private int macdSignal;

    /** 唐奇安通道進場週期（預設 20） */
    @Column(nullable = false)
    private int donchianEntry;

    /** 唐奇安通道出場週期（預設 10） */
    @Column(nullable = false)
    private int donchianExit;

    // ======== 風控參數 ========

    /** 停損百分比（例如 0.02 = 2%） */
    @Column(nullable = false)
    private double stopLossPct;

    /** 最大持倉天數 */
    @Column(nullable = false)
    private int maxHoldingDays;

    /** 初始資金（USD） */
    @Column(nullable = false)
    private double initialCapital;

    /** 每日最大交易次數 */
    @Column(nullable = false)
    private int maxTradesPerDay;

    /** 槓桿倍數 */
    @Column(nullable = false)
    private int leverage;

    /** 移動停利啟動門檻百分比 */
    @Column(nullable = false)
    private double trailingActivatePct;

    /** 移動停利偏移百分比 */
    @Column(nullable = false)
    private double trailingOffsetPct;

    /** 時間止損天數（持倉超過此天數仍虧損則出場） */
    @Column(nullable = false)
    private int timeStopDays;

    /** 交易冷卻天數（同幣對連續交易間的等待時間） */
    @Column(nullable = false)
    private int cooldownDays;

    /** 每次進場使用的資金比例（0.0~1.0，例如 0.5 表示 50%） */
    @Column(nullable = false, columnDefinition = "double precision default 1.0")
    @Builder.Default
    private double positionSizePct = 1.0;

    // ======== RSI 進出場參數 ========

    /** 做多進場 RSI 下限 */
    @Column(nullable = false)
    private double rsiLongEntryMin;

    /** 做多進場 RSI 上限 */
    @Column(nullable = false)
    private double rsiLongEntryMax;

    /** 做空進場 RSI 下限 */
    @Column(nullable = false)
    private double rsiShortEntryMin;

    /** 做空進場 RSI 上限 */
    @Column(nullable = false)
    private double rsiShortEntryMax;

    /** 做多出場 RSI 極端值 */
    @Column(nullable = false)
    private double rsiLongExitExtreme;

    /** 做空出場 RSI 極端值 */
    @Column(nullable = false)
    private double rsiShortExitExtreme;

    // ======== 時間戳 ========

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * 將此模板的參數轉換為 {@link TradingStrategyProperties}。
     * 用於 BacktestService 的 {@code runBacktestWithParams()} 方法，
     * 讓回測引擎可以使用用戶自訂的策略參數而非全域注入的參數。
     *
     * @return 對應此模板參數的 TradingStrategyProperties 實例
     */
    public TradingStrategyProperties toProperties() {
        return new TradingStrategyProperties(
                new TradingStrategyProperties.StrategyParams(
                        emaShort, emaLong, rsiPeriod,
                        macdShort, macdLong, macdSignal,
                        donchianEntry, donchianExit
                ),
                new TradingStrategyProperties.RiskParams(
                        stopLossPct, maxHoldingDays, initialCapital,
                        maxTradesPerDay, leverage,
                        trailingActivatePct, trailingOffsetPct,
                        timeStopDays, cooldownDays, positionSizePct
                ),
                new TradingStrategyProperties.RsiParams(
                        rsiLongEntryMin, rsiLongEntryMax,
                        rsiShortEntryMin, rsiShortEntryMax,
                        rsiLongExitExtreme, rsiShortExitExtreme
                )
        );
    }

    /**
     * 從 {@link TradingStrategyProperties} 建立 StrategyTemplate 的 builder。
     * 用於從 application.yml 的全域配置生成系統預設模板。
     *
     * @param props 來源的策略參數
     * @return 預填所有參數欄位的 builder
     */
    public static StrategyTemplateBuilder fromProperties(TradingStrategyProperties props) {
        var s = props.strategy();
        var r = props.risk();
        var rsi = props.rsi();

        return StrategyTemplate.builder()
                .emaShort(s.emaShort()).emaLong(s.emaLong())
                .rsiPeriod(s.rsiPeriod())
                .macdShort(s.macdShort()).macdLong(s.macdLong()).macdSignal(s.macdSignal())
                .donchianEntry(s.donchianEntry()).donchianExit(s.donchianExit())
                .stopLossPct(r.stopLossPct()).maxHoldingDays(r.maxHoldingDays())
                .initialCapital(r.initialCapital()).maxTradesPerDay(r.maxTradesPerDay())
                .leverage(r.leverage())
                .trailingActivatePct(r.trailingActivatePct()).trailingOffsetPct(r.trailingOffsetPct())
                .timeStopDays(r.timeStopDays()).cooldownDays(r.cooldownDays())
                .positionSizePct(r.positionSizePct())
                .rsiLongEntryMin(rsi.longEntryMin()).rsiLongEntryMax(rsi.longEntryMax())
                .rsiShortEntryMin(rsi.shortEntryMin()).rsiShortEntryMax(rsi.shortEntryMax())
                .rsiLongExitExtreme(rsi.longExitExtreme()).rsiShortExitExtreme(rsi.shortExitExtreme());
    }
}
