package com.aiinpocket.btctrade.model.dto;

public record DailyRewardResult(
        boolean claimed,
        long expAwarded,
        String message
) {}
