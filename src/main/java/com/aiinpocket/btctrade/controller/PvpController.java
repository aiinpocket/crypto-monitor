package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.PvpArenaService;
import com.aiinpocket.btctrade.service.PvpArenaService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PVP 競技場 Controller。
 * 提供頁面渲染和 REST API。
 */
@Controller
@RequiredArgsConstructor
public class PvpController {

    private final PvpArenaService pvpArenaService;

    /** PVP 頁面 */
    @GetMapping("/pvp")
    public String pvpPage(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("user", principal.getAppUser());
        return "pvp";
    }

    /** 取得競技場狀態 */
    @GetMapping("/api/user/pvp/status")
    @ResponseBody
    public ResponseEntity<ArenaStatus> getStatus(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.ok(pvpArenaService.getArenaStatus(principal.getUserId()));
    }

    /** 尋找對手 */
    @GetMapping("/api/user/pvp/opponents")
    @ResponseBody
    public ResponseEntity<List<OpponentInfo>> findOpponents(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.ok(pvpArenaService.findOpponents(principal.getUserId()));
    }

    /** 發起挑戰 */
    @PostMapping("/api/user/pvp/battle")
    @ResponseBody
    public ResponseEntity<BattleResult> battle(
            @RequestBody Map<String, Long> body,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        Long opponentId = body.get("opponentId");
        if (opponentId == null) {
            return ResponseEntity.badRequest().body(BattleResult.fail("未指定對手"));
        }
        BattleResult result = pvpArenaService.battle(principal.getUserId(), opponentId);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    /** PVP 排行榜 */
    @GetMapping("/api/user/pvp/leaderboard")
    @ResponseBody
    public ResponseEntity<List<PvpLeaderboardEntry>> leaderboard() {
        return ResponseEntity.ok(pvpArenaService.getLeaderboard());
    }

    /** PVP 戰績歷史 */
    @GetMapping("/api/user/pvp/history")
    @ResponseBody
    public ResponseEntity<List<PvpHistoryEntry>> history(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.ok(pvpArenaService.getHistory(principal.getUserId()));
    }
}
