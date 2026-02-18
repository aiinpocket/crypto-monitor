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

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.lastLoginAt = Instant.now();
    }
}
