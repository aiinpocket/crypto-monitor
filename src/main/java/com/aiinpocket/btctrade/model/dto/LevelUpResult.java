package com.aiinpocket.btctrade.model.dto;

import java.util.List;

public record LevelUpResult(
        boolean leveledUp,
        int oldLevel,
        int newLevel,
        long currentExp,
        long expToNextLevel,
        List<String> unlockedAchievements
) {}
