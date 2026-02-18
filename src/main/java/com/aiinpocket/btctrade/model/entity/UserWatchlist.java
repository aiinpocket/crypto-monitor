package com.aiinpocket.btctrade.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 使用者觀察清單 Entity。
 * 每位使用者可以從全域的 TrackedSymbol 池中，選擇自己要觀察的交易對。
 * K 線和市場數據是共享的，但每位使用者看到的幣對清單是獨立的。
 */
@Entity
@Table(name = "user_watchlist", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "symbol"})
}, indexes = {
        @Index(name = "idx_watchlist_symbol", columnList = "symbol")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserWatchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬使用者（多對一關聯） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** 觀察的交易對符號，例如 "BTCUSDT"（必須已存在於 TrackedSymbol） */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** 排序順序，用於前端側邊欄排列（數字越小越前面） */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    /** 加入觀察清單的時間 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
