package com.aiinpocket.btctrade.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 應用程式使用者 Entity。
 * 透過 Google OAuth2 登入時自動建立或更新。
 * 每位使用者擁有自己的觀察清單、通知管道設定、策略模板等。
 */
@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"oauth_provider", "oauth_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** OAuth 提供者名稱，目前固定為 "GOOGLE" */
    @Column(name = "oauth_provider", nullable = false, length = 20)
    private String oauthProvider;

    /** OAuth 提供者回傳的唯一識別碼（Google 的 subject ID） */
    @Column(name = "oauth_id", nullable = false, length = 100)
    private String oauthId;

    /** 使用者的電子郵件地址（來自 Google 帳戶） */
    @Column(nullable = false, length = 100)
    private String email;

    /** 使用者的顯示名稱（來自 Google 個人資料） */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /** 使用者的頭像 URL（來自 Google 個人資料） */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** 使用者角色：USER（一般）或 ADMIN（管理員） */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "USER";

    /** 帳戶建立時間（首次 OAuth 登入時設定，不可更新） */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 最後一次登入時間（每次 OAuth 登入時更新） */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    // ===== 遊戲化欄位 =====

    /** 角色等級（從 1 開始） */
    @Column(nullable = false)
    @Builder.Default
    private Integer level = 1;

    /** 累計經驗值 */
    @Column(nullable = false)
    @Builder.Default
    private Long experience = 0L;

    /** 累計登入天數 */
    @Column(name = "total_logins", nullable = false)
    @Builder.Default
    private Integer totalLogins = 0;

    /** 每日獎勵最後領取日期（防止同日重複領取） */
    @Column(name = "last_daily_reward_date")
    private LocalDate lastDailyRewardDate;

    /** 角色職業：WARRIOR / MAGE / RANGER / ASSASSIN */
    @Column(name = "character_class", nullable = false, length = 20)
    @Builder.Default
    private String characterClass = "WARRIOR";

    /** 當前啟用的策略模板 ID（null 表示尚未選擇，需強制引導至角色創建頁面） */
    @Column(name = "active_strategy_template_id")
    private Long activeStrategyTemplateId;

    // ===== 怪物戰鬥系統欄位 =====

    /** 遊戲幣餘額（賣裝備獲得，用於擴充倉庫/資遣隊員） */
    @Column(name = "game_currency", nullable = false, columnDefinition = "bigint default 0")
    @Builder.Default
    private Long gameCurrency = 0L;

    /** 倉庫欄位數（起始 100，每次擴充 +5） */
    @Column(name = "inventory_slots", nullable = false, columnDefinition = "integer default 100")
    @Builder.Default
    private Integer inventorySlots = 100;

    /** 隊伍人數上限（透過等級解鎖，最多 4） */
    @Column(name = "max_party_size", nullable = false, columnDefinition = "integer default 1")
    @Builder.Default
    private Integer maxPartySize = 1;

    // ===== PVP 競技場欄位 =====

    /** PVP 積分（Elo-like，預設 1000） */
    @Column(name = "pvp_rating", nullable = false, columnDefinition = "integer default 1000")
    @Builder.Default
    private Integer pvpRating = 1000;

    /** PVP 勝場數 */
    @Column(name = "pvp_wins", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer pvpWins = 0;

    /** PVP 敗場數 */
    @Column(name = "pvp_losses", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer pvpLosses = 0;

    // ===== 體力系統欄位 =====

    /** 當前體力值 */
    @Column(nullable = false, columnDefinition = "integer default 50")
    @Builder.Default
    private Integer stamina = 50;

    /** 體力上限 */
    @Column(name = "max_stamina", nullable = false, columnDefinition = "integer default 50")
    @Builder.Default
    private Integer maxStamina = 50;

    /** 上次體力回復時間（用於計算時間回復） */
    @Column(name = "last_stamina_regen_at")
    private Instant lastStaminaRegenAt;

    // ===== 隱私設定欄位 =====

    /** 是否在排行榜/PVP 中隱藏真實名稱 */
    @Column(name = "hide_profile_name", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean hideProfileName = false;

    /** 是否在排行榜/PVP 中隱藏頭像 */
    @Column(name = "hide_profile_avatar", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean hideProfileAvatar = false;

    /** 自訂頭像 base64 data URL（縮圖，約 10~20KB） */
    @Column(name = "custom_avatar_data", columnDefinition = "TEXT")
    private String customAvatarData;

    /**
     * 取得顯示用頭像 URL（優先自訂頭像）。
     */
    public String getEffectiveAvatarUrl() {
        if (customAvatarData != null && !customAvatarData.isEmpty()) {
            return customAvatarData;
        }
        return avatarUrl;
    }

    /**
     * 取得隱私保護後的顯示名稱。
     * 對外展示用（排行榜/PVP），自己看到的仍是原名。
     */
    public String getPublicDisplayName() {
        if (hideProfileName) {
            return "匿名冒險者 #" + id;
        }
        return displayName;
    }

    /**
     * 取得隱私保護後的頭像 URL。
     */
    public String getPublicAvatarUrl() {
        if (hideProfileAvatar) {
            return null; // 前端用首字母代替
        }
        return getEffectiveAvatarUrl();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.lastLoginAt = Instant.now();
    }
}
