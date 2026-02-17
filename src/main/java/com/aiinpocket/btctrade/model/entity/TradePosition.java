package com.aiinpocket.btctrade.model.entity;

import com.aiinpocket.btctrade.model.enums.ExitReason;
import com.aiinpocket.btctrade.model.enums.PositionDirection;
import com.aiinpocket.btctrade.model.enums.PositionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trade_position", indexes = {
        @Index(name = "idx_position_status", columnList = "status"),
        @Index(name = "idx_position_entry_time", columnList = "entry_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradePosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PositionDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PositionStatus status;

    @Column(name = "entry_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal entryPrice;

    @Column(name = "entry_time", nullable = false)
    private Instant entryTime;

    @Column(name = "exit_price", precision = 20, scale = 8)
    private BigDecimal exitPrice;

    @Column(name = "exit_time")
    private Instant exitTime;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "capital_used", nullable = false, precision = 20, scale = 2)
    private BigDecimal capitalUsed;

    @Column(name = "stop_loss_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal stopLossPrice;

    @Column(name = "realized_pnl", precision = 20, scale = 2)
    private BigDecimal realizedPnl;

    @Column(name = "return_pct", precision = 10, scale = 4)
    private BigDecimal returnPct;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_reason", length = 30)
    private ExitReason exitReason;

    @Column(name = "is_backtest", nullable = false)
    private boolean backtest;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
