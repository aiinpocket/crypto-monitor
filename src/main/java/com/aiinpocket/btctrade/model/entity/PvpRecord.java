package com.aiinpocket.btctrade.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * PVP 對戰紀錄 Entity。
 * 記錄每場 PVP 對戰的結果、雙方戰力、獎勵等。
 */
@Entity
@Table(name = "pvp_record", indexes = {
        @Index(name = "idx_pvp_attacker", columnList = "attacker_id"),
        @Index(name = "idx_pvp_defender", columnList = "defender_id"),
        @Index(name = "idx_pvp_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PvpRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 挑戰者 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attacker_id", nullable = false)
    private AppUser attacker;

    /** 被挑戰者 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "defender_id", nullable = false)
    private AppUser defender;

    /** 挑戰者是否勝利 */
    @Column(name = "attacker_won", nullable = false)
    private boolean attackerWon;

    /** 挑戰者總戰力 */
    @Column(name = "attacker_power", nullable = false)
    private int attackerPower;

    /** 被挑戰者總戰力 */
    @Column(name = "defender_power", nullable = false)
    private int defenderPower;

    /** 戰鬥回合數 */
    @Column(nullable = false)
    @Builder.Default
    private int rounds = 0;

    /** 挑戰者獲得的金幣（負數為失敗扣除） */
    @Column(name = "gold_reward", nullable = false)
    @Builder.Default
    private long goldReward = 0;

    /** 挑戰者獲得的經驗值 */
    @Column(name = "exp_reward", nullable = false)
    @Builder.Default
    private int expReward = 0;

    /** 挑戰者的 rating 變化 */
    @Column(name = "attacker_rating_change", nullable = false)
    @Builder.Default
    private int attackerRatingChange = 0;

    /** 被挑戰者的 rating 變化 */
    @Column(name = "defender_rating_change", nullable = false)
    @Builder.Default
    private int defenderRatingChange = 0;

    /** 戰鬥記錄 JSON（回合詳情） */
    @Column(name = "battle_log", columnDefinition = "TEXT")
    private String battleLog;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
