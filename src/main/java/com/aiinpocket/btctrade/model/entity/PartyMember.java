package com.aiinpocket.btctrade.model.entity;

import com.aiinpocket.btctrade.model.enums.CharacterClass;
import com.aiinpocket.btctrade.model.enums.Gender;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 隊伍成員（同伴角色）。
 * 每位用戶最多 4 位同伴，透過等級和成就解鎖。
 * 同伴不影響交易策略，但可以裝備收集到的武器/防具。
 * 每位成員有自己的種族、性別、職業組合和獨立裝備欄。
 */
@Entity
@Table(name = "party_member", indexes = {
        @Index(name = "idx_party_user", columnList = "user_id"),
        @Index(name = "idx_party_user_slot", columnList = "user_id, slot")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬用戶 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** 同伴名稱（用戶自訂） */
    @Column(nullable = false, length = 30)
    private String name;

    /** 種族 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "species_id", nullable = false)
    private Species species;

    /** 性別 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    /** 職業 */
    @Enumerated(EnumType.STRING)
    @Column(name = "character_class", nullable = false, length = 10)
    private CharacterClass characterClass;

    /** 隊伍欄位（1-4） */
    @Column(nullable = false)
    private Integer slot;

    /** 是否啟用（在隊伍中） */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** 加入時間 */
    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    @PrePersist
    protected void onCreate() {
        if (this.unlockedAt == null) {
            this.unlockedAt = Instant.now();
        }
    }
}
