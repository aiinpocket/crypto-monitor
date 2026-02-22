package com.aiinpocket.btctrade.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 用戶背包中的裝備實例。
 * 每件裝備可以被裝備到某個隊伍成員身上，或留在倉庫中。
 * 多餘的裝備可賣出換遊戲幣，但不能裝備在多個成員上。
 */
@Entity
@Table(name = "user_equipment", indexes = {
        @Index(name = "idx_user_equip_user", columnList = "user_id"),
        @Index(name = "idx_user_equip_member", columnList = "equipped_by_member_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 擁有者 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** 裝備定義 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_template_id", nullable = false)
    private EquipmentTemplate equipmentTemplate;

    /** 裝備中的隊伍成員（null = 在倉庫中） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipped_by_member_id")
    private PartyMember equippedByMember;

    /** 來源戰鬥紀錄（null = 非戰鬥獲得，如初始裝備） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_encounter_id")
    private MonsterEncounter sourceEncounter;

    /** 是否裝備在用戶角色身上（P2 簡化版，P4 隊伍系統前使用） */
    @Column(name = "equipped_by_user", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean equippedByUser = false;

    /** 取得時間 */
    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    // ===== 隨機 roll 的裝備數值 =====

    @Column(name = "stat_atk") @Builder.Default private Integer statAtk = 0;
    @Column(name = "stat_def") @Builder.Default private Integer statDef = 0;
    @Column(name = "stat_spd") @Builder.Default private Integer statSpd = 0;
    @Column(name = "stat_luck") @Builder.Default private Integer statLuck = 0;
    @Column(name = "stat_hp") @Builder.Default private Integer statHp = 0;

    /** 計算裝備總戰力 */
    public int getTotalPower() {
        return (statAtk != null ? statAtk : 0)
             + (statDef != null ? statDef : 0)
             + (statSpd != null ? statSpd : 0)
             + (statLuck != null ? statLuck : 0)
             + (statHp != null ? statHp : 0);
    }

    @PrePersist
    protected void onCreate() {
        if (this.acquiredAt == null) {
            this.acquiredAt = Instant.now();
        }
    }
}
