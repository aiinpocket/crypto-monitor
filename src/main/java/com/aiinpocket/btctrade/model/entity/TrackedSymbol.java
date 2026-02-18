package com.aiinpocket.btctrade.model.entity;

import com.aiinpocket.btctrade.model.enums.SyncStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "tracked_symbol", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"symbol"})
}, indexes = {
        @Index(name = "idx_tracked_active_status", columnList = "active, sync_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackedSymbol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, unique = true)
    private String symbol;

    @Column(name = "display_name", length = 50)
    private String displayName;

    @Column(nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    private SyncStatus syncStatus;

    @Column(name = "sync_progress")
    private Integer syncProgress;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "consecutive_error_count", nullable = false)
    @Builder.Default
    private int consecutiveErrorCount = 0;

    @Column(name = "delisted_at")
    private Instant delistedAt;

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
