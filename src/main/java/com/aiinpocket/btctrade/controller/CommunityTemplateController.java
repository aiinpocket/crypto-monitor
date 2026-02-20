package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.dto.CommunityTemplateDetail;
import com.aiinpocket.btctrade.model.entity.CommunityTemplate;
import com.aiinpocket.btctrade.model.entity.StrategyTemplate;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.CommunityTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 社群模板 REST API。
 * 提供社群模板的分享、瀏覽、投票和使用功能。
 */
@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityTemplateController {

    private final CommunityTemplateService communityService;

    /**
     * 查詢所有公開的社群模板（含當前用戶的投票狀態）。
     */
    @GetMapping("/templates")
    public List<CommunityTemplateDetail> getTemplates(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        Long userId = principal.getUserId();
        return communityService.getActiveTemplates().stream()
                .map(ct -> toDetail(ct, userId))
                .toList();
    }

    /**
     * 查詢我分享的社群模板（含 HIDDEN）。
     */
    @GetMapping("/my-submissions")
    public List<CommunityTemplateDetail> getMySubmissions(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        Long userId = principal.getUserId();
        return communityService.getMySubmissions(userId).stream()
                .map(ct -> toDetail(ct, userId))
                .toList();
    }

    /**
     * 分享模板到社群。
     */
    @PostMapping("/share")
    public ResponseEntity<?> shareTemplate(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        try {
            Long templateId = ((Number) body.get("strategyTemplateId")).longValue();
            String customName = (String) body.get("name");

            CommunityTemplate result = communityService.shareTemplate(
                    templateId, principal.getAppUser(), customName);

            return ResponseEntity.ok(Map.of(
                    "id", result.getId(),
                    "displayName", result.getDisplayName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "分享失敗，請稍後重試"));
        }
    }

    /**
     * 投票（讚/噓）。
     */
    @PostMapping("/vote")
    public ResponseEntity<?> vote(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        try {
            Long communityTemplateId = ((Number) body.get("communityTemplateId")).longValue();
            boolean thumbsUp = (Boolean) body.get("thumbsUp");

            CommunityTemplate result = communityService.vote(
                    communityTemplateId, principal.getUserId(), thumbsUp);

            return ResponseEntity.ok(Map.of(
                    "upvoteCount", result.getUpvoteCount(),
                    "downvoteCount", result.getDownvoteCount(),
                    "status", result.getStatus()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 使用（克隆）社群模板。
     */
    @PostMapping("/use")
    public ResponseEntity<?> useTemplate(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        try {
            Long communityTemplateId = ((Number) body.get("communityTemplateId")).longValue();

            StrategyTemplate clone = communityService.useTemplate(
                    communityTemplateId, principal.getAppUser());

            return ResponseEntity.ok(Map.of(
                    "id", clone.getId(),
                    "name", clone.getName(),
                    "message", "已將社群模板加入你的策略列表"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 刪除自己分享的社群模板。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSubmission(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id) {
        try {
            communityService.deleteSubmission(id, principal.getUserId());
            return ResponseEntity.ok(Map.of("message", "社群模板已刪除"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 查詢我的社群模板額度。
     */
    @GetMapping("/quota")
    public CommunityTemplateService.QuotaInfo getQuota(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return communityService.getQuotaInfo(principal.getUserId());
    }

    // ── 轉換方法 ──

    private CommunityTemplateDetail toDetail(CommunityTemplate ct, Long currentUserId) {
        Boolean myVote = communityService.getUserVote(ct.getId(), currentUserId);
        boolean isMine = ct.getSubmitter().getId().equals(currentUserId);
        return new CommunityTemplateDetail(
                ct.getId(),
                ct.getStrategyTemplate().getId(),
                ct.getDisplayName(),
                ct.getDescription(),
                ct.getSubmitter().getDisplayName(),
                ct.getSubmitter().getAvatarUrl(),
                ct.getUpvoteCount(),
                ct.getDownvoteCount(),
                ct.getUsageCount(),
                ct.getStatus(),
                myVote,
                isMine,
                ct.getCreatedAt()
        );
    }
}
