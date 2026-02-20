package com.aiinpocket.btctrade.model.dto;

import java.time.Instant;

/**
 * 社群模板詳情 DTO。
 * 用於前端列表和詳情頁面顯示，包含提交者資訊和投票狀態。
 */
public record CommunityTemplateDetail(
        Long id,
        Long strategyTemplateId,
        String displayName,
        String description,
        String submitterName,
        String submitterAvatarUrl,
        int upvoteCount,
        int downvoteCount,
        int usageCount,
        String status,
        Boolean myVote,
        boolean isMine,
        Instant createdAt
) {}
