package com.aiinpocket.btctrade.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_achievement", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "achievement_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "achievement_key", nullable = false, length = 50)
    private String achievementKey;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean seen = false;

    @PrePersist
    protected void onCreate() {
        if (this.unlockedAt == null) {
            this.unlockedAt = Instant.now();
        }
    }
}
