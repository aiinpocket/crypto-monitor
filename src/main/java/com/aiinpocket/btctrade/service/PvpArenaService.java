package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.*;
import com.aiinpocket.btctrade.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PVP 競技場服務。
 * 純裝備數值對戰，與策略無關。
 * 支援非同步匹配（挑戰其他玩家的裝備配置）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PvpArenaService {

    private final AppUserRepository userRepo;
    private final UserEquipmentRepository userEquipRepo;
    private final PvpRecordRepository pvpRecordRepo;
    private final PartyMemberRepository memberRepo;
    private final GamificationService gamificationService;
    private final ObjectMapper objectMapper;

    /** PVP 消耗體力 */
    private static final int PVP_STAMINA_COST = 5;
    /** 每分鐘回復體力 */
    private static final int STAMINA_REGEN_MINUTES = 3;
    /** Elo K-factor */
    private static final int ELO_K = 32;

    /**
     * 取得 PVP 競技場狀態。
     */
    public ArenaStatus getArenaStatus(Long userId) {
        AppUser user = userRepo.findById(userId).orElseThrow();
        regenStamina(user);

        long totalWins = pvpRecordRepo.countAttackerWins(userId) + pvpRecordRepo.countDefenderWins(userId);
        long totalLosses = (pvpRecordRepo.countByAttackerId(userId) - pvpRecordRepo.countAttackerWins(userId))
                + (pvpRecordRepo.countByDefenderId(userId) - pvpRecordRepo.countDefenderWins(userId));

        int teamPower = calculateTeamPower(userId);

        return new ArenaStatus(
                user.getPvpRating(),
                (int) totalWins, (int) totalLosses,
                user.getStamina(), user.getMaxStamina(),
                PVP_STAMINA_COST, teamPower,
                user.getLevel(), user.getCharacterClass(),
                user.getDisplayName()
        );
    }

    /**
     * 尋找對手。回傳最多 3 個候選人。
     */
    public List<OpponentInfo> findOpponents(Long userId) {
        AppUser user = userRepo.findById(userId).orElseThrow();
        int myRating = user.getPvpRating();
        int myPower = calculateTeamPower(userId);

        // 尋找 rating 接近的其他玩家（±300 範圍）
        List<AppUser> candidates = userRepo.findPvpOpponents(userId, myRating - 300, myRating + 300);

        if (candidates.isEmpty()) {
            // 擴大搜尋範圍
            candidates = userRepo.findPvpOpponents(userId, myRating - 600, myRating + 600);
        }

        // 隨機選 3 個
        Collections.shuffle(candidates);
        return candidates.stream()
                .limit(3)
                .map(c -> {
                    int opPower = calculateTeamPower(c.getId());
                    return new OpponentInfo(
                            c.getId(), c.getPublicDisplayName(), c.getPublicAvatarUrl(),
                            c.getLevel(), c.getCharacterClass(),
                            c.getPvpRating(), opPower,
                            powerDiffLabel(myPower, opPower)
                    );
                })
                .toList();
    }

    /**
     * 執行 PVP 對戰。
     */
    @Transactional
    public BattleResult battle(Long attackerId, Long defenderId) {
        if (attackerId.equals(defenderId)) {
            return BattleResult.fail("不能挑戰自己");
        }

        AppUser attacker = userRepo.findById(attackerId).orElseThrow();
        AppUser defender = userRepo.findById(defenderId).orElseThrow();

        // 回復體力
        regenStamina(attacker);

        // 檢查體力
        if (attacker.getStamina() < PVP_STAMINA_COST) {
            return BattleResult.fail("體力不足（需要 " + PVP_STAMINA_COST + "，目前 " + attacker.getStamina() + "）");
        }

        // 扣除體力
        attacker.setStamina(attacker.getStamina() - PVP_STAMINA_COST);

        // 計算雙方隊伍數值
        TeamStats atkStats = calculateTeamStats(attackerId);
        TeamStats defStats = calculateTeamStats(defenderId);

        // 執行戰鬥
        CombatResult combat = resolveCombat(atkStats, defStats);

        // 計算 Elo rating 變化
        int[] ratingChanges = calculateEloChange(attacker.getPvpRating(), defender.getPvpRating(), combat.attackerWon);

        // 計算獎勵
        int expReward = 0;
        long goldReward = 0;
        if (combat.attackerWon) {
            expReward = 10 + defender.getLevel();
            goldReward = 5L + (long) defender.getLevel() * 2;
            attacker.setPvpWins(attacker.getPvpWins() + 1);
            defender.setPvpLosses(defender.getPvpLosses() + 1);
        } else {
            goldReward = -Math.min(3L, attacker.getGameCurrency());
            attacker.setPvpLosses(attacker.getPvpLosses() + 1);
            defender.setPvpWins(defender.getPvpWins() + 1);
        }

        // 更新 rating
        attacker.setPvpRating(Math.max(0, attacker.getPvpRating() + ratingChanges[0]));
        defender.setPvpRating(Math.max(0, defender.getPvpRating() + ratingChanges[1]));

        // 更新金幣
        attacker.setGameCurrency(Math.max(0, attacker.getGameCurrency() + goldReward));

        userRepo.save(attacker);
        userRepo.save(defender);

        // 發放經驗值
        if (expReward > 0) {
            gamificationService.awardExp(attacker, expReward, "PVP_VICTORY");
        }

        // 序列化戰鬥記錄
        String battleLog;
        try {
            battleLog = objectMapper.writeValueAsString(combat.rounds);
        } catch (Exception e) {
            battleLog = "[]";
        }

        // 儲存紀錄
        PvpRecord record = PvpRecord.builder()
                .attacker(attacker)
                .defender(defender)
                .attackerWon(combat.attackerWon)
                .attackerPower(atkStats.totalPower())
                .defenderPower(defStats.totalPower())
                .rounds(combat.rounds.size())
                .goldReward(goldReward)
                .expReward(expReward)
                .attackerRatingChange(ratingChanges[0])
                .defenderRatingChange(ratingChanges[1])
                .battleLog(battleLog)
                .build();
        pvpRecordRepo.save(record);

        log.info("[PVP] {} ({}力) vs {} ({}力) → {} | Rating: {}({:+d}) vs {}({:+d})",
                attacker.getDisplayName(), atkStats.totalPower(),
                defender.getDisplayName(), defStats.totalPower(),
                combat.attackerWon ? "WIN" : "LOSE",
                attacker.getPvpRating(), ratingChanges[0],
                defender.getPvpRating(), ratingChanges[1]);

        return new BattleResult(
                true, combat.attackerWon ? "勝利！" : "失敗...",
                combat.attackerWon,
                atkStats.totalPower(), defStats.totalPower(),
                combat.rounds, combat.rounds.size(),
                expReward, goldReward, ratingChanges[0],
                attacker.getPvpRating(),
                attacker.getDisplayName(), defender.getPublicDisplayName(),
                attacker.getCharacterClass(), defender.getCharacterClass(),
                attacker.getLevel(), defender.getLevel()
        );
    }

    /**
     * 取得 PVP 排行榜（Top 20，按 rating 排序）。
     */
    public List<PvpLeaderboardEntry> getLeaderboard() {
        return userRepo.findTop20ByPvpRating().stream()
                .map(u -> new PvpLeaderboardEntry(
                        u.getPublicDisplayName(), u.getPublicAvatarUrl(),
                        u.getLevel(), u.getCharacterClass(),
                        u.getPvpRating(), u.getPvpWins(), u.getPvpLosses(),
                        calculateTeamPower(u.getId())
                ))
                .toList();
    }

    /**
     * 取得用戶的 PVP 歷史紀錄。
     */
    public List<PvpHistoryEntry> getHistory(Long userId) {
        return pvpRecordRepo.findRecentByUserId(userId).stream()
                .map(r -> {
                    boolean isAttacker = r.getAttacker().getId().equals(userId);
                    boolean won = isAttacker ? r.isAttackerWon() : !r.isAttackerWon();
                    AppUser opponent = isAttacker ? r.getDefender() : r.getAttacker();
                    int ratingChange = isAttacker ? r.getAttackerRatingChange() : r.getDefenderRatingChange();
                    return new PvpHistoryEntry(
                            opponent.getPublicDisplayName(), opponent.getLevel(),
                            opponent.getCharacterClass(), won,
                            isAttacker ? r.getAttackerPower() : r.getDefenderPower(),
                            isAttacker ? r.getDefenderPower() : r.getAttackerPower(),
                            r.getRounds(), ratingChange,
                            isAttacker ? r.getGoldReward() : 0,
                            isAttacker ? r.getExpReward() : 0,
                            r.getCreatedAt().toString()
                    );
                })
                .toList();
    }

    // ===== 內部計算方法 =====

    /**
     * 計算用戶隊伍總戰力（主角 + 隊員的所有裝備數值合計）。
     */
    int calculateTeamPower(Long userId) {
        // 主角裝備
        List<UserEquipment> userEquipped = userEquipRepo.findByUserIdAndEquippedByUserTrue(userId);
        int power = userEquipped.stream().mapToInt(UserEquipment::getTotalPower).sum();

        // 隊員裝備
        List<PartyMember> members = memberRepo.findByUserIdAndActiveTrue(userId);
        for (PartyMember m : members) {
            List<UserEquipment> memberEquipped = userEquipRepo.findByEquippedByMemberId(m.getId());
            power += memberEquipped.stream().mapToInt(UserEquipment::getTotalPower).sum();
        }

        return power;
    }

    /**
     * 計算隊伍戰鬥數值。
     */
    private TeamStats calculateTeamStats(Long userId) {
        int atk = 0, def = 0, spd = 0, luck = 0, hp = 0;

        // 主角裝備
        for (UserEquipment ue : userEquipRepo.findByUserIdAndEquippedByUserTrue(userId)) {
            atk += n(ue.getStatAtk());
            def += n(ue.getStatDef());
            spd += n(ue.getStatSpd());
            luck += n(ue.getStatLuck());
            hp += n(ue.getStatHp());
        }

        // 隊員裝備
        List<PartyMember> members = memberRepo.findByUserIdAndActiveTrue(userId);
        for (PartyMember m : members) {
            for (UserEquipment ue : userEquipRepo.findByEquippedByMemberId(m.getId())) {
                atk += n(ue.getStatAtk());
                def += n(ue.getStatDef());
                spd += n(ue.getStatSpd());
                luck += n(ue.getStatLuck());
                hp += n(ue.getStatHp());
            }
        }

        // 基礎值：無裝備也能打（等級加成）
        AppUser user = userRepo.findById(userId).orElseThrow();
        int lvBonus = user.getLevel() * 2;
        atk += lvBonus;
        def += lvBonus;
        hp += 50 + user.getLevel() * 5;

        return new TeamStats(atk, def, spd, luck, hp, atk + def + spd + luck + hp);
    }

    /**
     * 戰鬥結算。回合制自動戰鬥。
     */
    private CombatResult resolveCombat(TeamStats atk, TeamStats dfs) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<Map<String, Object>> rounds = new ArrayList<>();

        int atkHp = atk.hp();
        int dfsHp = dfs.hp();
        int maxRounds = 20;

        for (int i = 1; i <= maxRounds && atkHp > 0 && dfsHp > 0; i++) {
            Map<String, Object> round = new LinkedHashMap<>();
            round.put("round", i);

            // SPD 決定先手
            boolean atkFirst = atk.spd() + rng.nextInt(10) >= dfs.spd() + rng.nextInt(10);

            if (atkFirst) {
                dfsHp = applyAttack(round, "attacker", atk, dfs, dfsHp, rng);
                if (dfsHp > 0) {
                    atkHp = applyAttack(round, "defender", dfs, atk, atkHp, rng);
                }
            } else {
                atkHp = applyAttack(round, "defender", dfs, atk, atkHp, rng);
                if (atkHp > 0) {
                    dfsHp = applyAttack(round, "attacker", atk, dfs, dfsHp, rng);
                }
            }

            round.put("atkHp", Math.max(0, atkHp));
            round.put("dfsHp", Math.max(0, dfsHp));
            rounds.add(round);
        }

        // 如果 20 回合沒分出勝負，HP 高的那方贏
        boolean attackerWon = atkHp >= dfsHp;
        return new CombatResult(attackerWon, rounds);
    }

    private int applyAttack(Map<String, Object> round, String prefix,
                            TeamStats attacker, TeamStats defender, int targetHp,
                            ThreadLocalRandom rng) {
        // 基礎傷害 = ATK - DEF/2，最少 1
        int baseDmg = Math.max(1, attacker.atk() - defender.def() / 2);
        // ±20% 隨機浮動
        int dmg = (int) (baseDmg * (0.8 + rng.nextDouble() * 0.4));

        // 暴擊判定（LUCK 影響）
        double critChance = 0.1 + attacker.luck() * 0.003;
        boolean crit = rng.nextDouble() < critChance;
        if (crit) dmg = (int) (dmg * 1.5);

        // 迴避判定（LUCK 影響）
        double dodgeChance = 0.05 + defender.luck() * 0.002;
        boolean dodged = rng.nextDouble() < dodgeChance;

        if (dodged) {
            round.put(prefix + "Dmg", 0);
            round.put(prefix + "Crit", false);
            round.put(prefix + "Dodge", true);
            return targetHp;
        }

        dmg = Math.max(1, dmg);
        round.put(prefix + "Dmg", dmg);
        round.put(prefix + "Crit", crit);
        round.put(prefix + "Dodge", false);
        return targetHp - dmg;
    }

    /**
     * Elo 積分計算。
     * @return [attackerChange, defenderChange]
     */
    private int[] calculateEloChange(int attackerRating, int defenderRating, boolean attackerWon) {
        double expectedA = 1.0 / (1.0 + Math.pow(10, (defenderRating - attackerRating) / 400.0));
        double scoreA = attackerWon ? 1.0 : 0.0;
        int changeA = (int) Math.round(ELO_K * (scoreA - expectedA));
        return new int[]{changeA, -changeA};
    }

    /**
     * 體力回復（基於時間）。
     */
    private void regenStamina(AppUser user) {
        if (user.getStamina() >= user.getMaxStamina()) return;
        Instant lastRegen = user.getLastStaminaRegenAt();
        if (lastRegen == null) {
            user.setLastStaminaRegenAt(Instant.now());
            return;
        }

        long minutesPassed = Duration.between(lastRegen, Instant.now()).toMinutes();
        int regenAmount = (int) (minutesPassed / STAMINA_REGEN_MINUTES);
        if (regenAmount > 0) {
            int newStamina = Math.min(user.getMaxStamina(), user.getStamina() + regenAmount);
            user.setStamina(newStamina);
            user.setLastStaminaRegenAt(Instant.now());
            userRepo.save(user);
        }
    }

    private String powerDiffLabel(int myPower, int opPower) {
        if (opPower == 0 && myPower == 0) return "均勢";
        double ratio = myPower > 0 ? (double) opPower / myPower : 2.0;
        if (ratio > 1.3) return "強敵";
        if (ratio > 1.1) return "略強";
        if (ratio > 0.9) return "均勢";
        if (ratio > 0.7) return "略弱";
        return "弱敵";
    }

    private static int n(Integer v) { return v != null ? v : 0; }

    // ===== Record DTOs =====

    public record ArenaStatus(
            int pvpRating, int wins, int losses,
            int stamina, int maxStamina, int staminaCost,
            int teamPower, int level, String characterClass,
            String displayName
    ) {}

    public record OpponentInfo(
            Long id, String displayName, String avatarUrl,
            int level, String characterClass,
            int pvpRating, int teamPower, String diffLabel
    ) {}

    public record BattleResult(
            boolean success, String message, boolean won,
            int myPower, int opponentPower,
            List<Map<String, Object>> rounds, int totalRounds,
            int expReward, long goldReward, int ratingChange,
            int newRating,
            String myName, String opponentName,
            String myClass, String opponentClass,
            int myLevel, int opponentLevel
    ) {
        public static BattleResult fail(String msg) {
            return new BattleResult(false, msg, false, 0, 0,
                    List.of(), 0, 0, 0, 0, 0, "", "", "", "", 0, 0);
        }
    }

    public record PvpLeaderboardEntry(
            String displayName, String avatarUrl,
            int level, String characterClass,
            int pvpRating, int wins, int losses, int teamPower
    ) {}

    public record PvpHistoryEntry(
            String opponentName, int opponentLevel, String opponentClass,
            boolean won, int myPower, int opponentPower,
            int rounds, int ratingChange,
            long goldReward, int expReward,
            String time
    ) {}

    record TeamStats(int atk, int def, int spd, int luck, int hp, int totalPower) {}
    record CombatResult(boolean attackerWon, List<Map<String, Object>> rounds) {}
}
