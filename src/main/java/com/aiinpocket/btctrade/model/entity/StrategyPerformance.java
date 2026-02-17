package com.aiinpocket.btctrade.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 策略績效指標 Entity。
 * 儲存每個策略模板在各時間區段的回測績效數據，
 * 供前端策略對比表使用。
 */
@Entity
@Table(name = "strategy_performance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_perf_template_period_symbol",
                columnNames = {"strategy_template_id", "period_key", "symbol"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_template_id", nullable = false)
    private StrategyTemplate strategyTemplate;

    /** 基準幣對（預設 BTCUSDT） */
    @Column(length = 20, nullable = false)
    private String symbol;

    /** 時段 key（SINCE_2021, RECENT_5Y 等） */
    @Column(name = "period_key", length = 20, nullable = false)
    private String periodKey;

    /** 時段中文顯示名 */
    @Column(name = "period_label", length = 20, nullable = false)
    private String periodLabel;

    /** 回測起始時間 */
    @Column(nullable = false)
    private Instant periodStart;

    /** 回測結束時間 */
    @Column(nullable = false)
    private Instant periodEnd;

    /** 勝率 0.0~1.0 */
    @Column(precision = 6, scale = 4)
    private BigDecimal winRate;

    /** 累計報酬率 */
    @Column(precision = 10, scale = 6)
    private BigDecimal totalReturn;

    /** 年化報酬率 */
    @Column(precision = 10, scale = 4)
    private BigDecimal annualizedReturn;

    /** 最大回撤 */
    @Column(precision = 10, scale = 4)
    private BigDecimal maxDrawdown;

    /** 夏普比率 */
    @Column(precision = 10, scale = 4)
    private BigDecimal sharpeRatio;

    /** 交易筆數 */
    @Column(nullable = false)
    private int totalTrades;

    /** 未平倉浮盈百分比（nullable） */
    @Column(precision = 10, scale = 6)
    private BigDecimal unrealizedPnlPct;

    /** 未平倉方向 LONG/SHORT（nullable） */
    @Column(length = 10)
    private String unrealizedDirection;

    /** 計算時間 */
    @Column(nullable = false)
    private Instant computedAt;
}
