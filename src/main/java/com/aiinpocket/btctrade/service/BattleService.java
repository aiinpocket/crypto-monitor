package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.*;
import com.aiinpocket.btctrade.model.enums.AchievementDef;
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
import java.util.stream.Collectors;

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
    private final UserMonsterDiscoveryRepository discoveryRepo;
    private final GamificationService gamificationService;

    // 戰敗金幣懲罰比例（損失當前金幣的 5%）
    private static final double DEFEAT_GOLD_PENALTY_PCT = 0.05;
    // 最低金幣懲罰
    private static final long MIN_GOLD_PENALTY = 5L;

    /**
     * 交易開倉時觸發：為所有觀察該幣對的用戶建立怪物遭遇。
     *
     * @param symbol         幣對符號
     * @param volatility     近期波動率（ATR % 或類似指標）
     * @param entryTime      開倉時間
     * @param tradeDirection 交易方向（"LONG" / "SHORT"）
     * @param entryPrice     開倉價格
     * @return 建立的遭遇數量
     */
    @Transactional
    public int startEncounters(String symbol, double volatility, Instant entryTime,
                               String tradeDirection, BigDecimal entryPrice) {
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
                        .tradeDirection(tradeDirection)
                        .entryPrice(entryPrice)
                        .build();
                encounterRepo.save(encounter);
                recordDiscovery(watcher.getUser(), monster);
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
     * 交易平倉時觸發：結算所有進行中的遭遇，並檢查是否觸發特殊事件怪物。
     *
     * @param symbol    幣對符號
     * @param profitPct 交易報酬率（正數=獲利，負數=虧損）
     * @param exitTime  平倉時間
     * @param exitPrice 平倉價格
     */
    @Transactional
    public void resolveEncounters(String symbol, BigDecimal profitPct,
                                  Instant exitTime, BigDecimal exitPrice) {
        List<MonsterEncounter> allInProgress = encounterRepo
                .findBySymbolAndResult(symbol, BattleResult.IN_PROGRESS);

        boolean isVictory = profitPct.compareTo(BigDecimal.ZERO) > 0;

        // 收集受影響的用戶（去重）
        Set<AppUser> affectedUsers = new HashSet<>();

        for (MonsterEncounter encounter : allInProgress) {
            try {
                resolveOne(encounter, profitPct, isVictory, exitTime, exitPrice);
                affectedUsers.add(encounter.getUser());
            } catch (Exception e) {
                log.error("[戰鬥] 結算遭遇 {} 失敗: {}", encounter.getId(), e.getMessage());
            }
        }

        // 檢查是否觸發特殊事件怪物
        double pctValue = profitPct.doubleValue();
        if (Math.abs(pctValue) >= 0.20) {
            for (AppUser user : affectedUsers) {
                try {
                    triggerEventMonster(user, symbol, pctValue, exitTime, exitPrice, profitPct);
                } catch (Exception e) {
                    log.error("[特殊事件] 觸發事件怪物失敗 userId={}: {}", user.getId(), e.getMessage());
                }
            }
        }

        log.info("[戰鬥] {} 平倉 → {} 場遭遇結算完畢（{}）",
                symbol, allInProgress.size(), isVictory ? "勝利" : "戰敗");
    }

    /**
     * 觸發特殊事件怪物。
     * 獲利 ≥ 20%：召喚獲利事件怪物 → 必勝 → 中等機率掉落傳說裝備
     * 虧損 ≤ -20%：召喚虧損事件怪物 → 必敗 → 獲得特殊稱號
     */
    private void triggerEventMonster(AppUser user, String symbol, double pctValue,
                                      Instant exitTime, BigDecimal exitPrice, BigDecimal profitPct) {
        List<Monster> eventMonsters = monsterRepo.findByEventOnlyTrue();
        if (eventMonsters.isEmpty()) return;

        // 找到最匹配的事件怪物（門檻最高但不超過實際損益的）
        Monster bestMatch = null;
        if (pctValue > 0) {
            // 獲利事件：找正門檻中最大且 ≤ pctValue 的
            for (Monster m : eventMonsters) {
                if (m.getProfitThreshold() != null && m.getProfitThreshold() > 0
                        && pctValue >= m.getProfitThreshold()) {
                    if (bestMatch == null || m.getProfitThreshold() > bestMatch.getProfitThreshold()) {
                        bestMatch = m;
                    }
                }
            }
        } else {
            // 虧損事件：找負門檻中最小（絕對值最大）且 ≥ pctValue 的
            for (Monster m : eventMonsters) {
                if (m.getProfitThreshold() != null && m.getProfitThreshold() < 0
                        && pctValue <= m.getProfitThreshold()) {
                    if (bestMatch == null || m.getProfitThreshold() < bestMatch.getProfitThreshold()) {
                        bestMatch = m;
                    }
                }
            }
        }

        if (bestMatch == null) return;

        boolean isProfit = pctValue > 0;

        // 建立特殊遭遇（立即結算）
        MonsterEncounter encounter = MonsterEncounter.builder()
                .user(user)
                .monster(bestMatch)
                .symbol(symbol)
                .startedAt(exitTime)
                .endedAt(exitTime)
                .entryPrice(exitPrice)
                .exitPrice(exitPrice)
                .profitPct(profitPct)
                .tradeDirection(isProfit ? "EVENT_PROFIT" : "EVENT_LOSS")
                .build();

        if (isProfit) {
            // 獲利事件怪物：必勝
            encounter.setResult(BattleResult.VICTORY);
            int exp = bestMatch.getExpReward();
            long gold = (long) bestMatch.getLevel() * 20;
            encounter.setExpGained(exp);
            encounter.setGoldGained(gold);

            user.setGameCurrency(user.getGameCurrency() + gold);
            userRepo.save(user);
            gamificationService.awardExp(user, exp, "EVENT_MONSTER_VICTORY");

            // 中等機率掉落傳說裝備（40% 機率）
            rollEventEquipmentDrop(encounter);

            encounter.setBattleLog(generateEventBattleLog(bestMatch, user, true, pctValue));

            // 獲利事件成就
            AchievementDef slayerAchievement = getSlayerAchievement(pctValue);
            if (slayerAchievement != null) {
                gamificationService.unlockAchievement(user, slayerAchievement);
            }

            log.info("[特殊事件] 用戶 {} 擊敗「{}」！獲利 {}% → +{} EXP, +{} G",
                    user.getId(), bestMatch.getName(), String.format("%.1f", pctValue * 100), exp, gold);
        } else {
            // 虧損事件怪物：必敗（不扣金幣，僅給稱號）
            encounter.setResult(BattleResult.DEFEAT);
            encounter.setGoldLost(0L);
            encounter.setBattleLog(generateEventBattleLog(bestMatch, user, false, pctValue));

            // 虧損事件稱號
            AchievementDef survivorAchievement = getSurvivorAchievement(pctValue);
            if (survivorAchievement != null) {
                gamificationService.unlockAchievement(user, survivorAchievement);
            }

            log.info("[特殊事件] 用戶 {} 遭遇「{}」！虧損 {}% → 獲得稱號",
                    user.getId(), bestMatch.getName(), String.format("%.1f", pctValue * 100));
        }

        encounterRepo.save(encounter);
        recordDiscovery(user, bestMatch);
    }

    /**
     * 事件怪物的傳說裝備掉落（40% 基礎機率）。
     */
    private void rollEventEquipmentDrop(MonsterEncounter encounter) {
        List<MonsterDrop> dropTable = dropRepo.findByMonsterId(encounter.getMonster().getId());
        if (dropTable.isEmpty()) return;

        if (ThreadLocalRandom.current().nextDouble() < 0.40) {
            // 從掉落表中隨機選一件
            MonsterDrop drop = dropTable.get(ThreadLocalRandom.current().nextInt(dropTable.size()));
            EquipmentTemplate template = drop.getEquipmentTemplate();

            AppUser user = encounter.getUser();
            long currentItems = userEquipRepo.countByUserId(user.getId());
            if (currentItems >= user.getInventorySlots()) {
                log.info("[特殊事件] 用戶 {} 背包已滿，傳說裝備丟失", user.getId());
                return;
            }

            UserEquipment item = UserEquipment.builder()
                    .user(user)
                    .equipmentTemplate(template)
                    .sourceEncounter(encounter)
                    .build();
            userEquipRepo.save(item);

            log.info("[特殊事件] 用戶 {} 獲得事件裝備「{}」({})！",
                    user.getId(), template.getName(), template.getRarity());
        }
    }

    /** 根據獲利幅度取得對應擊敗成就 */
    private AchievementDef getSlayerAchievement(double pctValue) {
        if (pctValue >= 0.40) return AchievementDef.SLAYER_40;
        if (pctValue >= 0.30) return AchievementDef.SLAYER_30;
        if (pctValue >= 0.20) return AchievementDef.SLAYER_20;
        return null;
    }

    /** 根據虧損幅度取得對應倖存稱號 */
    private AchievementDef getSurvivorAchievement(double pctValue) {
        if (pctValue <= -0.40) return AchievementDef.SURVIVOR_40;
        if (pctValue <= -0.30) return AchievementDef.SURVIVOR_30;
        if (pctValue <= -0.20) return AchievementDef.SURVIVOR_20;
        return null;
    }

    /**
     * 生成特殊事件戰鬥日誌。
     */
    private String generateEventBattleLog(Monster monster, AppUser user,
                                           boolean isVictory, double pctValue) {
        String monsterName = monster.getName();
        String pctStr = String.format("%.1f%%", Math.abs(pctValue * 100));

        if (isVictory) {
            String[] patterns = {
                    "⚡ 天空裂開一道金光！「%s」從異界裂縫中現身！冒險者以 %s 的驚人獲利之力，一擊將其擊敗！「%s」爆裂成無數金幣和寶物散落一地！",
                    "⚡ 大地震動！傳說中的「%s」被 %s 的獲利能量所召喚！冒險者與之展開史詩決鬥...最終冒險者的交易之力壓倒了「%s」，將其徹底征服！",
                    "⚡ 「%s」降臨！這是只有達成 %s 獲利的強者才有資格挑戰的存在！冒險者以精湛的交易技巧將「%s」斬殺，傳說裝備從其身軀中溢出！"
            };
            String pattern = patterns[ThreadLocalRandom.current().nextInt(patterns.length)];
            return String.format(pattern, monsterName, pctStr, monsterName);
        } else {
            String[] patterns = {
                    "💀 黑暗吞噬了一切...「%s」從 %s 虧損的深淵中甦醒！冒險者的一切攻擊都被吸入虛無...這是無法戰勝的存在。冒險者帶著傷痕和教訓撤退，但活著回來就是最大的勝利。",
                    "💀 末日降臨！「%s」由 %s 的虧損能量凝聚而成！冒險者奮力抵抗，但「%s」的力量如同市場崩盤般不可阻擋...冒險者被擊退，卻因此獲得了珍貴的生存經驗。",
                    "💀 「%s」出現了！%s 的虧損引來了這個不可名狀的存在！冒險者拼盡全力，但命運早已注定...敗北的冒險者拖著疲憊的身軀離開，心中銘記這次教訓。"
            };
            String pattern = patterns[ThreadLocalRandom.current().nextInt(patterns.length)];
            return String.format(pattern, monsterName, pctStr, monsterName);
        }
    }

    /**
     * 結算單場遭遇。
     */
    private void resolveOne(MonsterEncounter encounter, BigDecimal profitPct,
                            boolean isVictory, Instant exitTime, BigDecimal exitPrice) {
        encounter.setProfitPct(profitPct);
        encounter.setEndedAt(exitTime);
        encounter.setExitPrice(exitPrice);

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

        // 生成戰鬥日誌
        encounter.setBattleLog(generateBattleLog(encounter, user, monster, isVictory));

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
     * 依據波動率選擇怪物（排除特殊事件怪物）。
     */
    private Monster selectMonster(double volatility) {
        // 先查找波動率範圍匹配的怪物（排除事件怪物）
        List<Monster> matching = monsterRepo
                .findByMinVolatilityLessThanEqualAndMaxVolatilityGreaterThanEqual(
                        volatility, volatility)
                .stream()
                .filter(m -> !m.isEventOnly())
                .toList();

        if (matching.isEmpty()) {
            // 若無完全匹配，從所有非事件怪物中隨機選取
            List<Monster> all = monsterRepo.findAll().stream()
                    .filter(m -> !m.isEventOnly())
                    .toList();
            if (all.isEmpty()) return null;
            return all.get(ThreadLocalRandom.current().nextInt(all.size()));
        }

        return matching.get(ThreadLocalRandom.current().nextInt(matching.size()));
    }

    // ===== 戰鬥日誌生成 =====

    /**
     * 職業技能表：依職業和等級階段決定技能名稱。
     * 格式：SKILL_MAP[職業] = { {初級技能}, {中級技能}, {上級技能}, {極致技能} }
     */
    private static final Map<String, String[][]> SKILL_MAP = Map.of(
            "WARRIOR", new String[][]{
                    {"猛擊", "防禦姿態", "盾牌衝撞"},
                    {"旋風斬", "鐵壁防禦", "戰吼"},
                    {"破甲重擊", "堅不可摧", "泰坦之握"},
                    {"天崩地裂", "不朽戰魂", "弒神一擊"}
            },
            "MAGE", new String[][]{
                    {"初級火球", "冰凍術", "魔力箭"},
                    {"中級火球", "暴風雪", "雷電鏈"},
                    {"上級火球", "冰晶結界", "閃電風暴"},
                    {"烈焰地獄", "極寒領域", "末日審判"}
            },
            "RANGER", new String[][]{
                    {"精準射擊", "毒箭", "設置陷阱"},
                    {"連射箭", "毒霧箭", "自然之力"},
                    {"貫穿射擊", "致命毒液", "鷹眼追蹤"},
                    {"流星箭雨", "萬箭齊發", "神射手之眼"}
            },
            "ASSASSIN", new String[][]{
                    {"背刺", "毒刃", "暗影步"},
                    {"致命背刺", "劇毒之刃", "暗殺術"},
                    {"影分身", "死亡之舞", "夜影追蹤"},
                    {"極影滅殺", "絕命毒牙", "虛空暗殺"}
            }
    );

    /**
     * 根據等級取得技能階段 (0=初級, 1=中級, 2=上級, 3=極致)
     */
    private int getSkillTier(int userLevel) {
        if (userLevel >= 30) return 3;
        if (userLevel >= 20) return 2;
        if (userLevel >= 10) return 1;
        return 0;
    }

    /**
     * 隨機選取一個技能名稱
     */
    private String pickSkill(String characterClass, int userLevel) {
        String[][] skills = SKILL_MAP.getOrDefault(characterClass, SKILL_MAP.get("WARRIOR"));
        int tier = getSkillTier(userLevel);
        String[] tierSkills = skills[tier];
        return tierSkills[ThreadLocalRandom.current().nextInt(tierSkills.length)];
    }

    /**
     * 生成魔物獵人風格的戰鬥日誌（不顯示 HP）。
     */
    private String generateBattleLog(MonsterEncounter encounter, AppUser user,
                                     Monster monster, boolean isVictory) {
        String charClass = user.getCharacterClass();
        int level = user.getLevel();
        String monsterName = monster.getName();

        // 選取 2~3 個技能
        String skill1 = pickSkill(charClass, level);
        String skill2 = pickSkill(charClass, level);
        String skill3 = pickSkill(charClass, level);
        // 避免連續重複
        while (skill2.equals(skill1)) skill2 = pickSkill(charClass, level);

        StringBuilder log = new StringBuilder();

        if (isVictory) {
            // 勝利敘事（3 回合風格）
            String[] victoryPatterns = {
                    "冒險者對「%s」施展了【%s】，命中！怪物怒吼反擊！冒險者側身閃過，蓄力發動【%s】！%s搖搖欲墜...最終一擊！【%s】貫穿要害，%s轟然倒下！",
                    "「%s」擋住去路！冒險者以【%s】先發制人！怪物被擊退一步，冒險者乘勝追擊施展【%s】！%s發出痛苦嘶吼...冒險者抓住破綻，【%s】一擊必殺！%s化為塵埃！",
                    "冒險者遭遇「%s」！迅速展開【%s】攻勢！怪物負傷反撲，冒險者沉著應對，再施【%s】！%s體力不支...冒險者發動終結技【%s】！%s被完全擊潰！"
            };
            String pattern = victoryPatterns[ThreadLocalRandom.current().nextInt(victoryPatterns.length)];
            log.append(String.format(pattern, monsterName, skill1, skill2, monsterName, skill3, monsterName));
        } else {
            // 敗北敘事
            String[] defeatPatterns = {
                    "冒險者對「%s」施展【%s】，但被閃避！%s的猛烈攻擊命中！冒險者嘗試以【%s】反擊...但%s的力量太過強大，冒險者被迫撤退！",
                    "「%s」的氣勢壓倒一切！冒險者使用【%s】奮力抵抗，但效果不彰！%s發動猛攻！冒險者嘗試【%s】脫困...但寡不敵眾，冒險者被擊退！",
                    "冒險者以【%s】挑戰「%s」！初始攻勢被化解...%s展開反擊！冒險者在慌亂中施展【%s】，但為時已晚...冒險者不敵%s的壓倒性力量，敗退而歸！"
            };
            String pattern = defeatPatterns[ThreadLocalRandom.current().nextInt(defeatPatterns.length)];
            if (defeatPatterns[2].equals(pattern)) {
                log.append(String.format(pattern, skill1, monsterName, monsterName, skill2, monsterName));
            } else {
                log.append(String.format(pattern, monsterName, skill1, monsterName, skill2, monsterName));
            }
        }

        return log.toString();
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
     * 取得怪物圖鑑（含用戶發現狀態）。
     */
    public BestiaryResult getBestiary(Long userId) {
        List<Monster> all = monsterRepo.findAll();
        Set<Long> discovered = discoveryRepo.findDiscoveredMonsterIdsByUserId(userId);
        long totalMonsters = all.size();
        long discoveredCount = discovered.size();
        return new BestiaryResult(all, discovered, totalMonsters, discoveredCount);
    }

    /**
     * 記錄怪物發現（冪等，已發現則跳過）。
     * 使用 INSERT ON CONFLICT DO NOTHING 實現原子性冪等操作，
     * 從 DB 層面消除 Check-Then-Act 競態條件。
     */
    public void recordDiscovery(AppUser user, Monster monster) {
        discoveryRepo.discoverOrIgnore(user.getId(), monster.getId());
    }

    /**
     * 根據 monster ID 記錄發現（供冒險系統使用）。
     */
    public void recordDiscoveryById(AppUser user, Long monsterId) {
        discoveryRepo.discoverOrIgnore(user.getId(), monsterId);
    }

    public record BattleStats(long total, long victories, long defeats, double winRate) {}
    public record BestiaryResult(List<Monster> monsters, Set<Long> discoveredIds,
                                 long totalMonsters, long discoveredCount) {}
}
