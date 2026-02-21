package com.aiinpocket.btctrade.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 用戶怪物圖鑑發現記錄。
 * 追蹤用戶遭遇過哪些怪物，未發現的怪物在圖鑑中以「?」顯示。
 * 觸發來源：即時交易遭遇（BattleService）+ 回測冒險（BacktestAdventureService）。
 */
@Entity
@Table(name = "user_monster_discovery",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_monster_discovery",
                columnNames = {"user_id", "monster_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMonsterDiscovery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monster_id", nullable = false)
    private Monster monster;

    @Column(name = "discovered_at", nullable = false)
    @Builder.Default
    private Instant discoveredAt = Instant.now();
}
