package com.aiinpocket.btctrade.model.entity;

import com.aiinpocket.btctrade.model.enums.BattleResult;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 怪物遭遇紀錄（戰鬥日誌）。
 * 每筆交易信號觸發時會生成一場戰鬥，
 * 持倉期間為戰鬥進行中，平倉時決定勝負。
 */
@Entity
@Table(name = "monster_encounter", indexes = {
        @Index(name = "idx_encounter_user", columnList = "user_id"),
        @Index(name = "idx_encounter_user_result", columnList = "user_id, result"),
        @Index(name = "idx_encounter_started", columnList = "started_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonsterEncounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 關聯用戶 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** 遭遇的怪物 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monster_id", nullable = false)
    private Monster monster;

    /** 關聯的交易幣對 */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** 戰鬥結果 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private BattleResult result;

    /** 交易方向（LONG / SHORT） */
    @Column(name = "trade_direction", length = 10)
    private String tradeDirection;

    /** 開倉價格 */
    @Column(name = "entry_price", precision = 20, scale = 8)
    private BigDecimal entryPrice;

    /** 平倉價格 */
    @Column(name = "exit_price", precision = 20, scale = 8)
    private BigDecimal exitPrice;

    /** 交易報酬率百分比（平倉時填入） */
    @Column(name = "profit_pct", precision = 10, scale = 4)
    private BigDecimal profitPct;

    /** 戰鬥日誌文字（平倉時生成） */
    @Column(name = "battle_log", columnDefinition = "TEXT")
    private String battleLog;

    /** 本次戰鬥獲得的經驗值 */
    @Column(name = "exp_gained")
    @Builder.Default
    private Integer expGained = 0;

    /** 本次戰鬥獲得的遊戲幣 */
    @Column(name = "gold_gained")
    @Builder.Default
    private Long goldGained = 0L;

    /** 本次戰鬥失去的遊戲幣（戰敗懲罰） */
    @Column(name = "gold_lost")
    @Builder.Default
    private Long goldLost = 0L;

    /** 戰鬥開始時間（開倉時間） */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /** 戰鬥結束時間（平倉時間） */
    @Column(name = "ended_at")
    private Instant endedAt;
}
