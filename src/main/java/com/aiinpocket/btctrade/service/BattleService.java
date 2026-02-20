package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.*;
import com.aiinpocket.btctrade.model.enums.BattleResult;
import com.aiinpocket.btctrade.model.enums.Rarity;
import com.aiinpocket.btctrade.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 怪物戰鬥核心服務。
 * 負責：
 * 1. 依據市場波動度選擇怪物
 * 2. 在交易開倉時為訂閱用戶建立遭遇戰
 * 3. 在交易平倉時結算戰鬥結果（EXP/掉落/金幣）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BattleService {

    private final MonsterRepository monsterRepo;
    private final MonsterEncounterRepository encounterRepo;
    private final MonsterDropRepository dropRepo;
    private final UserEquipmentRepository userEquipRepo;
    private final UserWatchlistRepository watchlistRepo;
    private final AppUserRepository userRepo;
    private final GamificationService gamificationService;

    // 戰敗金幣懲罰比例（損失當前金幣的 5%）
    private static final double DEFEAT_GOLD_PENALTY_PCT = 0.05;
    // 最低金幣懲罰
    private static final long MIN_GOLD_PENALTY = 5L;

    /**
     * 交易開倉時觸發：為所有觀察該幣對的用戶建立怪物遭遇。
     *
     * @param symbol     幣對符號
     * @param volatility 近期波動率（ATR % 或類似指標）
     * @param entryTime  開倉時間
     * @return 建立的遭遇數量
     */
    @Transactional
    public int startEncounters(String symbol, double volatility, Instant entryTime) {
        // 1. 依波動度選擇怪物
        Monster monster = selectMonster(volatility);
        if (monster == null) {
            log.warn("[戰鬥] 找不到波動度 {} 對應的怪物，跳過", volatility);
            return 0;
        }

        // 2. 找出所有觀察此幣對的用戶
        List<UserWatchlist> watchers = watchlistRepo.findBySymbol(symbol);
        if (watchers.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (UserWatchlist watcher : watchers) {
            try {
                MonsterEncounter encounter = MonsterEncounter.builder()
                        .user(watcher.getUser())
                        .monster(monster)
                        .symbol(symbol)
                        .result(BattleResult.IN_PROGRESS)
                        .startedAt(entryTime)
                        .build();
                encounterRepo.save(encounter);
                count++;
            } catch (Exception e) {
                log.error("[戰鬥] 建立遭遇失敗 userId={}: {}", watcher.getUser().getId(), e.getMessage());
            }
        }

        log.info("[戰鬥] {} 開倉 → 怪物「{}」(Lv.{}) 出現！建立 {} 場遭遇",
                symbol, monster.getName(), monster.getLevel(), count);
        return count;
    }

    /**
     * 交易平倉時觸發：結算所有進行中的遭遇。
     *
     * @param symbol    幣對符號
     * @param profitPct 交易報酬率（正數=獲利，負數=虧損）
     * @param exitTime  平倉時間
     */
    @Transactional
    public void resolveEncounters(String symbol, BigDecimal profitPct, Instant exitTime) {
        List<MonsterEncounter> allInProgress = encounterRepo
                .findBySymbolAndResult(symbol, BattleResult.IN_PROGRESS);

        boolean isVictory = profitPct.compareTo(BigDecimal.ZERO) > 0;

        for (MonsterEncounter encounter : allInProgress) {
            try {
                resolveOne(encounter, profitPct, isVictory, exitTime);
            } catch (Exception e) {
                log.error("[戰鬥] 結算遭遇 {} 失敗: {}", encounter.getId(), e.getMessage());
            }
        }

        log.info("[戰鬥] {} 平倉 → {} 場遭遇結算完畢（{}）",
                symbol, allInProgress.size(), isVictory ? "勝利" : "戰敗");
    }

    /**
     * 結算單場遭遇。
     */
    private void resolveOne(MonsterEncounter encounter, BigDecimal profitPct,
                            boolean isVictory, Instant exitTime) {
        encounter.setProfitPct(profitPct);
        encounter.setEndedAt(exitTime);

        AppUser user = encounter.getUser();
        Monster monster = encounter.getMonster();

        if (isVictory) {
            encounter.setResult(BattleResult.VICTORY);

            // 經驗值 = 怪物固定 EXP
            int expGained = monster.getExpReward();
            encounter.setExpGained(expGained);

            // 金幣 = 怪物等級 × 10 + 報酬率加成
            long baseGold = (long) monster.getLevel() * 10;
            long bonusGold = (long) (profitPct.doubleValue() * 100 * monster.getLevel());
            long goldGained = Math.max(baseGold + bonusGold, baseGold);
            encounter.setGoldGained(goldGained);

            // 發放 EXP 和金幣
            user.setGameCurrency(user.getGameCurrency() + goldGained);
            userRepo.save(user);
            gamificationService.awardExp(user, expGained, "BATTLE_VICTORY");

            // 嘗試掉落裝備
            rollEquipmentDrop(encounter, profitPct.doubleValue());

            log.info("[戰鬥] 用戶 {} 擊敗「{}」→ +{} EXP, +{} 金幣",
                    user.getId(), monster.getName(), expGained, goldGained);
        } else {
            encounter.setResult(BattleResult.DEFEAT);

            // 戰敗懲罰：扣除金幣
            long penalty = Math.max(
                    (long) (user.getGameCurrency() * DEFEAT_GOLD_PENALTY_PCT),
                    MIN_GOLD_PENALTY);
            penalty = Math.min(penalty, user.getGameCurrency()); // 不扣到負數
            encounter.setGoldLost(penalty);

            user.setGameCurrency(user.getGameCurrency() - penalty);
            userRepo.save(user);

            log.info("[戰鬥] 用戶 {} 敗給「{}」→ -{} 金幣",
                    user.getId(), monster.getName(), penalty);
        }

        encounterRepo.save(encounter);
    }

    /**
     * 裝備掉落判定。
     * 報酬率越高，稀有裝備掉落機率越高。
     */
    private void rollEquipmentDrop(MonsterEncounter encounter, double profitPct) {
        List<MonsterDrop> dropTable = dropRepo.findByMonsterId(encounter.getMonster().getId());
        if (dropTable.isEmpty()) return;

        // 掉落倍率：報酬率 1% → 1x，5% → 3x，10% → 5x
        double dropMultiplier = 1.0 + Math.min(profitPct * 40, 4.0);

        for (MonsterDrop drop : dropTable) {
            EquipmentTemplate template = drop.getEquipmentTemplate();
            double effectiveRate = template.getDropRate() * dropMultiplier;

            // 稀有度越高，倍率效果越明顯
            if (template.getRarity() == Rarity.LEGENDARY) {
                effectiveRate *= 0.5; // 傳說級減半基礎率但受倍率加成更多
            }

            if (ThreadLocalRandom.current().nextDouble() < effectiveRate) {
                // 檢查背包容量
                AppUser user = encounter.getUser();
                long currentItems = userEquipRepo.countByUserId(user.getId());
                if (currentItems >= user.getInventorySlots()) {
                    log.info("[戰鬥] 用戶 {} 背包已滿（{}/{}），裝備掉落丟失",
                            user.getId(), currentItems, user.getInventorySlots());
                    return;
                }

                UserEquipment item = UserEquipment.builder()
                        .user(user)
                        .equipmentTemplate(template)
                        .sourceEncounter(encounter)
                        .build();
                userEquipRepo.save(item);

                log.info("[戰鬥] 用戶 {} 獲得裝備「{}」({})！",
                        user.getId(), template.getName(), template.getRarity());
                return; // 每場戰鬥最多掉一件
            }
        }
    }

    /**
     * 依據波動率選擇怪物。
     */
    private Monster selectMonster(double volatility) {
        // 先查找波動率範圍匹配的怪物
        List<Monster> matching = monsterRepo
                .findByMinVolatilityLessThanEqualAndMaxVolatilityGreaterThanEqual(
                        volatility, volatility);

        if (matching.isEmpty()) {
            // 若無完全匹配，取最接近的風險等級
            List<Monster> all = monsterRepo.findAll();
            if (all.isEmpty()) return null;
            return all.get(ThreadLocalRandom.current().nextInt(all.size()));
        }

        return matching.get(ThreadLocalRandom.current().nextInt(matching.size()));
    }

    // ===== 查詢 API =====

    /**
     * 取得用戶戰鬥紀錄（最新在前）。
     */
    public List<MonsterEncounter> getUserEncounters(Long userId) {
        return encounterRepo.findByUserIdOrderByStartedAtDesc(userId);
    }

    /**
     * 取得用戶戰鬥統計。
     */
    public BattleStats getUserBattleStats(Long userId) {
        long total = encounterRepo.countByUserId(userId);
        long victories = encounterRepo.countByUserIdAndResult(userId, BattleResult.VICTORY);
        long defeats = encounterRepo.countByUserIdAndResult(userId, BattleResult.DEFEAT);
        double winRate = total > 0 ? (double) victories / total * 100 : 0;
        return new BattleStats(total, victories, defeats, winRate);
    }

    /**
     * 取得怪物圖鑑（所有怪物定義）。
     */
    public List<Monster> getBestiary() {
        return monsterRepo.findAll();
    }

    public record BattleStats(long total, long victories, long defeats, double winRate) {}
}
