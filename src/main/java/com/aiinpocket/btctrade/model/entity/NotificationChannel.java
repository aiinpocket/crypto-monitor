package com.aiinpocket.btctrade.model.entity;

import com.aiinpocket.btctrade.model.enums.ChannelType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 使用者的通知管道設定 Entity。
 * 每位使用者可以設定多個通知管道（Discord、Gmail、Telegram），
 * 各管道的設定資訊以 JSON 格式儲存在 configJson 欄位中。
 *
 * <p>configJson 格式範例：
 * <ul>
 *   <li>Discord: {"botToken":"xxx","channelId":"123","targetUserId":"456"}</li>
 *   <li>Gmail: {"recipientEmail":"user@example.com"}</li>
 *   <li>Telegram: {"botToken":"xxx:yyy","chatId":"123456"}</li>
 * </ul>
 */
@Entity
@Table(name = "notification_channel", indexes = {
        @Index(name = "idx_notif_user_type", columnList = "user_id, channel_type"),
        @Index(name = "idx_notif_user_enabled", columnList = "user_id, enabled")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬使用者 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** 通知管道類型（DISCORD / GMAIL / TELEGRAM） */
    @Column(name = "channel_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChannelType channelType;

    /** 管道專屬的設定資訊（JSON 格式），各管道所需欄位不同 */
    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;

    /** 是否啟用此管道 */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    /** 是否在進場訊號時發送通知 */
    @Column(name = "notify_on_entry", nullable = false)
    @Builder.Default
    private boolean notifyOnEntry = true;

    /** 是否在出場訊號時發送通知 */
    @Column(name = "notify_on_exit", nullable = false)
    @Builder.Default
    private boolean notifyOnExit = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
