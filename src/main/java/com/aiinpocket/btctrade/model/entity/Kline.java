package com.aiinpocket.btctrade.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "kline", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"symbol", "interval_type", "open_time"})
}, indexes = {
        @Index(name = "idx_kline_symbol_interval_open_time",
                columnList = "symbol, interval_type, open_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Kline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "interval_type", nullable = false, length = 10)
    private String intervalType;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    @Column(name = "close_time", nullable = false)
    private Instant closeTime;

    @Column(name = "open_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal closePrice;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal volume;

    @Column(name = "quote_volume", precision = 30, scale = 8)
    private BigDecimal quoteVolume;

    @Column(name = "trade_count")
    private Integer tradeCount;

    @Column(name = "taker_buy_base_volume", precision = 30, scale = 8)
    private BigDecimal takerBuyBaseVolume;

    @Column(name = "taker_buy_quote_volume", precision = 30, scale = 8)
    private BigDecimal takerBuyQuoteVolume;
}
