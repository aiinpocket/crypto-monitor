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

    /** 取得時間 */
    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    @PrePersist
    protected void onCreate() {
        if (this.acquiredAt == null) {
            this.acquiredAt = Instant.now();
        }
    }
}
