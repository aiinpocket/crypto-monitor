package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.*;
import com.aiinpocket.btctrade.model.enums.BattleResult;
import com.aiinpocket.btctrade.model.enums.CharacterClass;
import com.aiinpocket.btctrade.model.enums.EquipmentType;
import com.aiinpocket.btctrade.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * GM 帳號每日自動活動服務。
 * 每天凌晨 2 點自動為每個 GM 帳號執行：
 * <ol>
 *   <li>10 次怪物遭遇（模擬探索，不需實際回測）</li>
 *   <li>自動裝備最佳武器和防具</li>
 *   <li>10 場 PVP 對戰（與真實玩家進行 Elo 對戰）</li>
 * </ol>
 *
 * <p>GM 不可遭遇特殊 BOSS（eventOnly 怪物）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GmDailyActivityService {

    private final AppUserRepository userRepo;
    private final MonsterRepository monsterRepo;
    private final MonsterEncounterRepository encounterRepo;
    private final UserEquipmentRepository userEquipRepo;
    private final EquipmentTemplateRepository equipTemplateRepo;
    private final PartyMemberRepository memberRepo;
    private final GamificationService gamificationService;
    private final EquipmentService equipmentService;
    private final PvpArenaService pvpArenaService;

    private static final int DAILY_ENCOUNTERS = 10;
    private static final int DAILY_PVP_BATTLES = 10;
    private static final double GM_WIN_RATE = 0.65;

    /**
     * 每天凌晨 2:00 (Asia/Taipei) 執行 GM 每日活動。
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Taipei")
    public void runDailyActivities() {
        log.info("[GM活動] 開始執行 GM 每日活動...");

        List<AppUser> gmAccounts = userRepo.findByOauthProvider("SYSTEM");
        if (gmAccounts.isEmpty()) {
            log.info("[GM活動] 沒有找到 GM 帳號，跳過");
            return;
        }

        for (AppUser gm : gmAccounts) {
            try {
                log.info("[GM活動] 開始處理 GM: {} (Lv.{}, 職業={})",
                        gm.getDisplayName(), gm.getLevel(), gm.getCharacterClass());

                // 1. 模擬怪物遭遇
                simulateMonsterEncounters(gm);

                // 2. 自動裝備最佳裝備
                autoEquipBestGear(gm);

                // 3. 執行 PVP 對戰
                runPvpBattles(gm);

                log.info("[GM活動] GM {} 每日活動完成", gm.getDisplayName());
            } catch (Exception e) {
                log.error("[GM活動] GM {} 活動執行失敗: {}", gm.getDisplayName(), e.getMessage(), e);
            }
        }

        log.info("[GM活動] 所有 GM 每日活動執行完畢");
    }

    /**
     * 模擬 10 次怪物遭遇。
     * 不需要實際回測，直接建立遭遇紀錄並發放獎勵。
     * GM 不可遭遇特殊 BOSS（eventOnly = true）。
     */
    @Transactional
    void simulateMonsterEncounters(AppUser gm) {
        List<Monster> regularMonsters = monsterRepo.findByEventOnlyFalse();
        if (regularMonsters.isEmpty()) {
            log.warn("[GM活動] 沒有可用的一般怪物");
            return;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int totalExp = 0;
        long totalGold = 0;
        int victories = 0;
        int equipDrops = 0;

        for (int i = 0; i < DAILY_ENCOUNTERS; i++) {
            Monster monster = regularMonsters.get(rng.nextInt(regularMonsters.size()));
            boolean victory = rng.nextDouble() < GM_WIN_RATE;

            int encounterExp = 0;
            long encounterGold = 0;
            long encounterGoldLost = 0;

            if (victory) {
                encounterExp = monster.getLevel() * 3;
                encounterGold = (long) monster.getLevel() * 8;
                totalExp += encounterExp;
                totalGold += encounterGold;
                victories++;
            } else {
                encounterGoldLost = Math.max((long) (monster.getLevel() * 2), 5);
            }

            // 建立遭遇紀錄
            Instant now = Instant.now();
            MonsterEncounter encounter = MonsterEncounter.builder()
                    .user(gm)
                    .monster(monster)
                    .symbol("BTCUSDT")
                    .result(victory ? BattleResult.VICTORY : BattleResult.DEFEAT)
                    .tradeDirection("GM_DAILY")
                    .startedAt(now)
                    .endedAt(now)
                    .expGained(encounterExp)
                    .goldGained(encounterGold)
                    .goldLost(encounterGoldLost)
                    .battleLog(victory
                            ? "【GM巡邏】" + gm.getDisplayName() + " 在日常巡邏中擊敗了「" + monster.getName() + "」(Lv." + monster.getLevel() + ")"
                            : "【GM巡邏】" + gm.getDisplayName() + " 遭遇「" + monster.getName() + "」(Lv." + monster.getLevel() + ")，不敵撤退")
                    .build();
            encounterRepo.save(encounter);

            // 勝利時 15% 機率掉落裝備
            if (victory && rng.nextDouble() < 0.15) {
                if (rollEquipmentDrop(gm, encounter)) {
                    equipDrops++;
                }
            }
        }

        // 發放獎勵
        gm.setGameCurrency(Math.max(0, gm.getGameCurrency() + totalGold));
        userRepo.save(gm);

        if (totalExp > 0) {
            gamificationService.awardExp(gm, totalExp, "GM_DAILY_PATROL");
        }

        log.info("[GM活動] {} 探索完成: 勝利 {}/{}, +{} EXP, +{} G, {} 件裝備掉落",
                gm.getDisplayName(), victories, DAILY_ENCOUNTERS, totalExp, totalGold, equipDrops);
    }

    /**
     * 自動為 GM 角色和隊員裝備最高戰力的裝備。
     */
    @Transactional
    void autoEquipBestGear(AppUser gm) {
        String gmClass = gm.getCharacterClass();
        int equipped = 0;

        // 為主角裝備最佳武器和防具
        for (EquipmentType type : EquipmentType.values()) {
            UserEquipment best = findBestUnequippedItem(gm.getId(), gmClass, type);
            if (best != null) {
                // 檢查是否比目前裝備更好
                Optional<UserEquipment> currentOpt = userEquipRepo.findEquippedByUserAndType(gm.getId(), type);
                if (currentOpt.isEmpty() || best.getTotalPower() > currentOpt.get().getTotalPower()) {
                    EquipmentService.EquipResult result = equipmentService.equipItem(gm.getId(), best.getId());
                    if (result.success()) {
                        equipped++;
                    }
                }
            }
        }

        // 為隊員裝備最佳裝備
        List<PartyMember> members = memberRepo.findByUserIdAndActiveTrue(gm.getId());
        for (PartyMember member : members) {
            String memberClass = member.getCharacterClass().name();
            for (EquipmentType type : EquipmentType.values()) {
                UserEquipment best = findBestUnequippedItem(gm.getId(), memberClass, type);
                if (best != null) {
                    Optional<UserEquipment> currentOpt = userEquipRepo.findEquippedByMemberAndType(member.getId(), type);
                    if (currentOpt.isEmpty() || best.getTotalPower() > currentOpt.get().getTotalPower()) {
                        EquipmentService.EquipResult result = equipmentService.equipItemToMember(gm.getId(), best.getId(), member.getId());
                        if (result.success()) {
                            equipped++;
                        }
                    }
                }
            }
        }

        if (equipped > 0) {
            log.info("[GM活動] {} 自動裝備了 {} 件更強裝備", gm.getDisplayName(), equipped);
        }
    }

    /**
     * 執行 10 場 PVP 對戰。
     * GM 體力會先回滿，然後依序尋找對手並進行戰鬥。
     */
    void runPvpBattles(AppUser gm) {
        // GM 體力回滿（NPC 不受體力限制）
        gm.setStamina(gm.getMaxStamina());
        gm.setLastStaminaRegenAt(Instant.now());
        userRepo.save(gm);

        int wins = 0, losses = 0, skipped = 0;

        for (int i = 0; i < DAILY_PVP_BATTLES; i++) {
            // 每場戰鬥前確保體力充足
            AppUser freshGm = userRepo.findById(gm.getId()).orElse(null);
            if (freshGm == null) break;

            if (freshGm.getStamina() < 5) {
                freshGm.setStamina(freshGm.getMaxStamina());
                freshGm.setLastStaminaRegenAt(Instant.now());
                userRepo.save(freshGm);
            }

            // 尋找對手（rating ±300 範圍內的真實玩家）
            int rating = freshGm.getPvpRating();
            List<AppUser> candidates = userRepo.findPvpOpponents(gm.getId(), rating - 300, rating + 300);
            if (candidates.isEmpty()) {
                candidates = userRepo.findPvpOpponents(gm.getId(), rating - 600, rating + 600);
            }
            if (candidates.isEmpty()) {
                skipped++;
                continue;
            }

            // 隨機選一個對手
            AppUser opponent = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

            try {
                PvpArenaService.BattleResult result = pvpArenaService.battle(gm.getId(), opponent.getId());
                if (result.success()) {
                    if (result.won()) wins++;
                    else losses++;
                }
            } catch (Exception e) {
                log.warn("[GM活動] {} PVP 第 {} 場失敗: {}", gm.getDisplayName(), i + 1, e.getMessage());
                skipped++;
            }
        }

        log.info("[GM活動] {} PVP 完成: {} 勝 {} 敗 {} 跳過",
                gm.getDisplayName(), wins, losses, skipped);
    }

    // ===== 內部方法 =====

    /**
     * 在 GM 背包中找到未裝備的最高戰力裝備（匹配職業和類型）。
     */
    private UserEquipment findBestUnequippedItem(Long userId, String characterClass, EquipmentType type) {
        List<UserEquipment> inventory = userEquipRepo.findByUserIdOrderByAcquiredAtDesc(userId);

        return inventory.stream()
                .filter(ue -> !Boolean.TRUE.equals(ue.getEquippedByUser()))
                .filter(ue -> ue.getEquippedByMember() == null)
                .filter(ue -> ue.getEquipmentTemplate().getEquipmentType() == type)
                .filter(ue -> {
                    CharacterClass restriction = ue.getEquipmentTemplate().getClassRestriction();
                    return restriction == null || restriction.name().equals(characterClass);
                })
                .max(Comparator.comparingInt(UserEquipment::getTotalPower))
                .orElse(null);
    }

    /**
     * 裝備掉落判定（與 BacktestAdventureService 邏輯一致）。
     */
    private boolean rollEquipmentDrop(AppUser gm, MonsterEncounter encounter) {
        List<EquipmentTemplate> allEquip = equipTemplateRepo.findAll();
        if (allEquip.isEmpty()) return false;

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 依稀有度加權選擇
        EquipmentTemplate selected = null;
        for (int i = 0; i < 3; i++) {
            EquipmentTemplate candidate = allEquip.get(rng.nextInt(allEquip.size()));
            double chance = switch (candidate.getRarity()) {
                case COMMON -> 0.5;
                case RARE -> 0.25;
                case EPIC -> 0.1;
                case LEGENDARY -> 0.03;
            };
            if (rng.nextDouble() < chance) {
                selected = candidate;
                break;
            }
        }
        if (selected == null) return false;

        // 檢查背包容量
        long currentItems = userEquipRepo.countByUserId(gm.getId());
        if (currentItems >= gm.getInventorySlots()) return false;

        equipmentService.createWithRolledStats(gm, selected, encounter);
        return true;
    }
}
