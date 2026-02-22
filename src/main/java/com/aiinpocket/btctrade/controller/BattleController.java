package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.entity.Monster;
import com.aiinpocket.btctrade.model.entity.MonsterEncounter;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.BattleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 戰鬥系統 REST API。
 * 提供戰鬥紀錄、統計和怪物圖鑑查詢。
 */
@RestController
@RequestMapping("/api/user/battle")
@RequiredArgsConstructor
public class BattleController {

    private final BattleService battleService;

    /** 取得當前用戶的戰鬥紀錄 */
    @GetMapping("/encounters")
    public ResponseEntity<List<EncounterDto>> getEncounters(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        List<MonsterEncounter> encounters = battleService.getUserEncounters(principal.getUserId());
        List<EncounterDto> dtos = encounters.stream().map(this::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    /** 取得戰鬥統計 */
    @GetMapping("/stats")
    public ResponseEntity<BattleService.BattleStats> getStats(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.ok(battleService.getUserBattleStats(principal.getUserId()));
    }

    /** 取得怪物圖鑑（含發現狀態） */
    @GetMapping("/bestiary")
    public ResponseEntity<BestiaryResponse> getBestiary(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        BattleService.BestiaryResult result = battleService.getBestiary(principal.getUserId());
        Set<Long> discovered = result.discoveredIds();
        List<MonsterDto> dtos = result.monsters().stream()
                .map(m -> toMonsterDto(m, discovered.contains(m.getId())))
                .toList();
        return ResponseEntity.ok(new BestiaryResponse(
                dtos, result.totalMonsters(), result.discoveredCount()));
    }

    // ===== DTO =====

    private EncounterDto toDto(MonsterEncounter e) {
        return new EncounterDto(
                e.getId(),
                e.getMonster().getName(),
                e.getMonster().getLevel(),
                e.getMonster().getPixelCssClass(),
                e.getMonster().getRiskTier().name(),
                e.getSymbol(),
                e.getResult().name(),
                e.getTradeDirection(),
                e.getEntryPrice() != null ? e.getEntryPrice().toPlainString() : null,
                e.getExitPrice() != null ? e.getExitPrice().toPlainString() : null,
                e.getProfitPct() != null ? e.getProfitPct().doubleValue() : null,
                e.getExpGained(),
                e.getGoldGained(),
                e.getGoldLost(),
                e.getBattleLog(),
                e.getStartedAt().toString(),
                e.getEndedAt() != null ? e.getEndedAt().toString() : null,
                e.getMonster().isEventOnly()
        );
    }

    private MonsterDto toMonsterDto(Monster m, boolean discovered) {
        return new MonsterDto(
                m.getId(), m.getName(), m.getDescription(),
                m.getRiskTier().name(), m.getLevel(),
                m.getHp(), m.getAtk(), m.getDef(),
                m.getExpReward(), m.getPixelCssClass(),
                discovered, m.isEventOnly()
        );
    }

    record EncounterDto(
            Long id, String monsterName, int monsterLevel,
            String monsterCssClass, String riskTier,
            String symbol, String result,
            String tradeDirection, String entryPrice, String exitPrice,
            Double profitPct, int expGained, long goldGained, long goldLost,
            String battleLog,
            String startedAt, String endedAt,
            boolean eventMonster
    ) {}

    record MonsterDto(
            Long id, String name, String description,
            String riskTier, int level,
            int hp, int atk, int def,
            int expReward, String pixelCssClass,
            boolean discovered, boolean eventOnly
    ) {}

    record BestiaryResponse(List<MonsterDto> monsters, long totalMonsters, long discoveredCount) {}
}
