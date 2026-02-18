package com.aiinpocket.btctrade.model.dto;

import java.util.List;

public record GamificationProfile(
        int level,
        long currentExp,
        long expToNextLevel,
        double expProgressPct,
        String characterClass,
        int totalLogins,
        boolean dailyRewardClaimed,
        List<UnlockedAchievement> achievements,
        List<PendingEvent> pendingEvents
) {
    public record UnlockedAchievement(
            String key,
            String displayName,
            String description,
            String unlockedAt,
            boolean seen
    ) {}

    public record PendingEvent(
            Long id,
            String eventType,
            String eventData,
            String createdAt
    ) {}
}
