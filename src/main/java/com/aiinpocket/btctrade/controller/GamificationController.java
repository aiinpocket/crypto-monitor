package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.dto.DailyRewardResult;
import com.aiinpocket.btctrade.model.dto.GamificationProfile;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.GamificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/gamification")
@RequiredArgsConstructor
public class GamificationController {

    private final GamificationService gamificationService;

    @GetMapping("/profile")
    public GamificationProfile getProfile(@AuthenticationPrincipal AppUserPrincipal principal) {
        return gamificationService.getProfile(principal.getUserId());
    }

    @PostMapping("/daily")
    public DailyRewardResult claimDailyReward(@AuthenticationPrincipal AppUserPrincipal principal) {
        return gamificationService.claimDailyReward(principal.getAppUser());
    }

    @GetMapping("/events")
    public List<GamificationProfile.PendingEvent> getEvents(@AuthenticationPrincipal AppUserPrincipal principal) {
        return gamificationService.getProfile(principal.getUserId()).pendingEvents();
    }

    @PostMapping("/events/seen")
    public ResponseEntity<Void> markEventsSeen(@AuthenticationPrincipal AppUserPrincipal principal) {
        gamificationService.markEventsSeen(principal.getUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/leaderboard")
    public List<GamificationService.LeaderboardEntry> getLeaderboard() {
        return gamificationService.getLeaderboard();
    }

    @PostMapping("/character-class")
    public ResponseEntity<Map<String, String>> changeCharacterClass(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody Map<String, String> body) {
        String characterClass = body.get("characterClass");
        if (characterClass == null || characterClass.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "請指定角色職業"));
        }
        try {
            gamificationService.changeCharacterClass(principal.getUserId(), characterClass.toUpperCase());
            return ResponseEntity.ok(Map.of("characterClass", characterClass.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
