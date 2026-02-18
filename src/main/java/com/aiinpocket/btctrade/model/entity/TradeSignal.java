package com.aiinpocket.btctrade.model.entity;

import com.aiinpocket.btctrade.model.enums.TradeAction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trade_signal", indexes = {
        @Index(name = "idx_signal_time", columnList = "signal_time"),
        @Index(name = "idx_signal_action", columnList = "action"),
        @Index(name = "idx_signal_symbol_backtest", columnList = "symbol, is_backtest"),
        @Index(name = "idx_signal_symbol_time", columnList = "symbol, signal_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "signal_time", nullable = false)
    private Instant signalTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeAction action;

    @Column(name = "close_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal closePrice;

    @Column(name = "ema_short", precision = 20, scale = 8)
    private BigDecimal emaShort;

    @Column(name = "ema_long", precision = 20, scale = 8)
    private BigDecimal emaLong;

    @Column(name = "rsi_value", precision = 10, scale = 4)
    private BigDecimal rsiValue;

    @Column(name = "macd_value", precision = 20, scale = 8)
    private BigDecimal macdValue;

    @Column(name = "macd_signal", precision = 20, scale = 8)
    private BigDecimal macdSignalValue;

    @Column(name = "macd_histogram", precision = 20, scale = 8)
    private BigDecimal macdHistogram;

    @Column(name = "is_backtest", nullable = false)
    private boolean backtest;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
