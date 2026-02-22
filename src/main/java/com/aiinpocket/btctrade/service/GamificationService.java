package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.dto.DailyRewardResult;
import com.aiinpocket.btctrade.model.dto.GamificationProfile;
import com.aiinpocket.btctrade.model.dto.GamificationProfile.PendingEvent;
import com.aiinpocket.btctrade.model.dto.GamificationProfile.UnlockedAchievement;
import com.aiinpocket.btctrade.model.dto.LevelUpResult;
import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.model.entity.GameEventLog;
import com.aiinpocket.btctrade.model.entity.UserAchievement;
import com.aiinpocket.btctrade.model.enums.AchievementDef;
import com.aiinpocket.btctrade.repository.AppUserRepository;
import com.aiinpocket.btctrade.repository.GameEventLogRepository;
import com.aiinpocket.btctrade.repository.UserAchievementRepository;
import com.aiinpocket.btctrade.repository.BacktestRunRepository;
import com.aiinpocket.btctrade.repository.StrategyTemplateRepository;
import com.aiinpocket.btctrade.repository.UserWatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationService {

    private final AppUserRepository userRepo;
    private final UserAchievementRepository achievementRepo;
    private final GameEventLogRepository eventRepo;
    private final BacktestRunRepository backtestRunRepo;
    private final StrategyTemplateRepository templateRepo;
    private final UserWatchlistRepository watchlistRepo;

    private static final long DAILY_REWARD_EXP = 15;
    private static final long LOGIN_EXP = 5;
    private static final ZoneId TZ = ZoneId.of("Asia/Taipei");

    /**
     * 計算升到指定等級所需的累計經驗值。
     * 公式: level * level * 50
     */
    public static long expForLevel(int level) {
        return (long) level * level * 50;
    }

    /**
     * 根據累計經驗值計算當前等級。
     */
    public static int levelForExp(long totalExp) {
        int level = 1;
        while (expForLevel(level + 1) <= totalExp) {
            level++;
        }
        return level;
    }

    /**
     * 獎勵經驗值，自動檢查升級和成就。
     */
    @Transactional
    public LevelUpResult awardExp(AppUser user, long amount, String reason) {
        // 重新從 DB 載入，避免 session 中的舊快照覆蓋其他欄位（如 pvpRating）
        user = userRepo.findById(user.getId()).orElseThrow();
        int oldLevel = user.getLevel();
        long newExp = user.getExperience() + amount;
        user.setExperience(newExp);

        int newLevel = levelForExp(newExp);
        boolean leveledUp = newLevel > oldLevel;

        if (leveledUp) {
            user.setLevel(newLevel);
            log.info("[遊戲化] 用戶 {} 升級: Lv.{} → Lv.{} (EXP: {})",
                    user.getId(), oldLevel, newLevel, newExp);

            // 記錄升級事件
            eventRepo.save(GameEventLog.builder()
                    .user(user)
                    .eventType("LEVEL_UP")
                    .eventData("{\"oldLevel\":" + oldLevel + ",\"newLevel\":" + newLevel + "}")
                    .build());
        }

        userRepo.save(user);

        // 檢查等級成就
        List<String> unlocked = new ArrayList<>();
        if (leveledUp) {
            unlocked.addAll(checkLevelAchievements(user, newLevel));
        }

        long expToNext = expForLevel(newLevel + 1) - newExp;
        return new LevelUpResult(leveledUp, oldLevel, newLevel, newExp, expToNext, unlocked);
    }

    /**
     * 領取每日登入獎勵。
     */
    @Transactional
    public DailyRewardResult claimDailyReward(AppUser user) {
        // 重新從 DB 載入，避免 session 快照覆蓋 DB 資料
        user = userRepo.findById(user.getId()).orElseThrow();
        LocalDate today = LocalDate.now(TZ);
        if (today.equals(user.getLastDailyRewardDate())) {
            return new DailyRewardResult(false, 0, "今日已領取獎勵");
        }

        user.setLastDailyRewardDate(today);
        awardExp(user, DAILY_REWARD_EXP, "DAILY_REWARD");

        eventRepo.save(GameEventLog.builder()
                .user(user)
                .eventType("DAILY_LOGIN")
                .eventData("{\"exp\":" + DAILY_REWARD_EXP + "}")
                .build());

        return new DailyRewardResult(true, DAILY_REWARD_EXP, "獲得每日獎勵 +" + DAILY_REWARD_EXP + " EXP！");
    }

    /**
     * 檢查並解鎖符合條件的成就。
     */
    @Transactional
    public List<AchievementDef> checkAndUnlockAchievements(AppUser user, String trigger) {
        List<AchievementDef> unlocked = new ArrayList<>();

        // 批次查詢已解鎖成就（一次查詢取代多次 existsBy）
        Set<String> alreadyUnlocked = achievementRepo.findByUserIdOrderByUnlockedAtDesc(user.getId())
                .stream()
                .map(UserAchievement::getAchievementKey)
                .collect(Collectors.toSet());

        switch (trigger) {
            case "LOGIN" -> {
                unlocked.addAll(tryUnlockBatch(user, AchievementDef.FIRST_LOGIN, user.getTotalLogins() >= 1, alreadyUnlocked));
                unlocked.addAll(tryUnlockBatch(user, AchievementDef.LOGIN_7D, user.getTotalLogins() >= 7, alreadyUnlocked));
                unlocked.addAll(tryUnlockBatch(user, AchievementDef.LOGIN_30D, user.getTotalLogins() >= 30, alreadyUnlocked));
                unlocked.addAll(tryUnlockBatch(user, AchievementDef.LOGIN_100D, user.getTotalLogins() >= 100, alreadyUnlocked));
            }
            case "BACKTEST" -> {
                long completedCount = eventRepo.countByUserIdAndEventType(user.getId(), "BACKTEST_COMPLETE")
                        + eventRepo.countByUserIdAndEventType(user.getId(), "BACKTEST_PROFIT");
                long profitCount = eventRepo.countByUserIdAndEventType(user.getId(), "BACKTEST_PROFIT");
                unlocked.addAll(tryUnlockBatch(user, AchievementDef.FIRST_BACKTEST, completedCount >= 1, alreadyUnlocked));
                unlocked.addAll(tryUnlockBatch(user, AchievementDef.PROFITABLE_1, profitCount >= 1, alreadyUnlocked));
                unlocked.addAll(tryUnlockBatch(user, AchievementDef.PROFITABLE_10, profitCount >= 10, alreadyUnlocked));
            }
            case "STRATEGY" -> {
                int templateCount = templateRepo.countByUserId(user.getId());
                unlocked.addAll(tryUnlockBatch(user, AchievementDef.STRATEGY_CLONE, templateCount >= 1, alreadyUnlocked));
                unlocked.addAll(tryUnlockBatch(user, AchievementDef.STRATEGY_5, templateCount >= 5, alreadyUnlocked));
            }
            case "WATCHLIST" -> {
                long watchlistCount = watchlistRepo.countByUserId(user.getId());
                unlocked.addAll(tryUnlockBatch(user, AchievementDef.WATCHLIST_5, watchlistCount >= 5, alreadyUnlocked));
            }
            case "BACKTEST_METRICS" -> {
                // 由呼叫端傳入具體指標，在此只做等級成就
            }
            default -> log.debug("[遊戲化] 未知觸發器: {}", trigger);
        }

        return unlocked;
    }

    /**
     * 檢查回測績效相關成就。
     */
    @Transactional
    public List<AchievementDef> checkBacktestMetricAchievements(AppUser user, double sharpeRatio, double annualReturn) {
        List<AchievementDef> unlocked = new ArrayList<>();
        if (sharpeRatio > 1.0) {
            unlocked.addAll(tryUnlock(user, AchievementDef.SHARPE_1, true));
        }
        if (annualReturn > 0.30) {
            unlocked.addAll(tryUnlock(user, AchievementDef.ANNUAL_30, true));
        }
        return unlocked;
    }

    /**
     * 取得用戶遊戲化個人檔案。
     */
    public GamificationProfile getProfile(Long userId) {
        AppUser user = userRepo.findById(userId).orElseThrow();
        long expToNext = expForLevel(user.getLevel() + 1) - user.getExperience();
        long levelStart = expForLevel(user.getLevel());
        long levelEnd = expForLevel(user.getLevel() + 1);
        double progressPct = (levelEnd - levelStart) > 0
                ? (double) (user.getExperience() - levelStart) / (levelEnd - levelStart) * 100
                : 0;

        LocalDate today = LocalDate.now(TZ);
        boolean dailyClaimed = today.equals(user.getLastDailyRewardDate());

        List<UnlockedAchievement> achievements = achievementRepo.findByUserIdOrderByUnlockedAtDesc(userId)
                .stream()
                .map(a -> {
                    AchievementDef def;
                    try {
                        def = AchievementDef.valueOf(a.getAchievementKey());
                    } catch (IllegalArgumentException e) {
                        return new UnlockedAchievement(a.getAchievementKey(), a.getAchievementKey(), "", a.getUnlockedAt().toString(), a.isSeen());
                    }
                    return new UnlockedAchievement(
                            a.getAchievementKey(),
                            def.getDisplayName(),
                            def.getDescription(),
                            a.getUnlockedAt().toString(),
                            a.isSeen()
                    );
                })
                .toList();

        List<PendingEvent> events = eventRepo.findByUserIdAndSeenFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(e -> new PendingEvent(e.getId(), e.getEventType(), e.getEventData(), e.getCreatedAt().toString()))
                .toList();

        return new GamificationProfile(
                user.getLevel(),
                user.getExperience(),
                expToNext,
                progressPct,
                user.getCharacterClass(),
                user.getTotalLogins(),
                dailyClaimed,
                achievements,
                events
        );
    }

    /**
     * 取得排行榜（Top 10）。
     */
    public List<LeaderboardEntry> getLeaderboard() {
        return userRepo.findTop10ByOrderByLevelDescExperienceDesc().stream()
                .map(u -> new LeaderboardEntry(
                        u.getPublicDisplayName(),
                        u.getLevel(),
                        u.getExperience(),
                        u.getCharacterClass(),
                        u.getPublicAvatarUrl()
                ))
                .toList();
    }

    /**
     * 標記所有未看事件為已看。
     */
    @Transactional
    public int markEventsSeen(Long userId) {
        return eventRepo.markAllSeenByUserId(userId);
    }

    /**
     * 更換角色職業。
     * 同時檢查：若用戶沒有自訂策略（使用系統預設），則自動切換為新職業的預設策略。
     */
    @Transactional
    public void changeCharacterClass(Long userId, String characterClass) {
        AppUser user = userRepo.findById(userId).orElseThrow();
        if (!List.of("WARRIOR", "MAGE", "RANGER", "ASSASSIN").contains(characterClass)) {
            throw new IllegalArgumentException("無效的角色職業: " + characterClass);
        }
        user.setCharacterClass(characterClass);

        // 若當前活躍策略是系統預設模板，自動切換為新職業的預設策略
        if (user.getActiveStrategyTemplateId() != null) {
            templateRepo.findById(user.getActiveStrategyTemplateId()).ifPresent(current -> {
                if (current.isSystemDefault()) {
                    templateRepo.findFirstByNameStartingWithAndSystemDefaultTrue(
                            getClassPrefix(characterClass)
                    ).ifPresent(newDefault -> {
                        user.setActiveStrategyTemplateId(newDefault.getId());
                        log.info("[遊戲化] 用戶 {} 換職業 {} → 活躍策略自動切換為 '{}'",
                                userId, characterClass, newDefault.getName());
                    });
                }
            });
        }

        userRepo.save(user);
    }

    private String getClassPrefix(String characterClass) {
        return switch (characterClass) {
            case "WARRIOR" -> "⚔️ 戰士";
            case "MAGE" -> "🔮 法師";
            case "RANGER" -> "🏹 遊俠";
            case "ASSASSIN" -> "🗡️ 刺客";
            default -> "";
        };
    }

    /**
     * 直接解鎖指定成就（供外部服務如 BattleService 呼叫）。
     * 冪等操作：已解鎖則跳過。
     */
    @Transactional
    public void unlockAchievement(AppUser user, AchievementDef def) {
        tryUnlock(user, def, true);
    }

    // ===== 內部方法 =====

    private List<AchievementDef> tryUnlockBatch(AppUser user, AchievementDef def, boolean condition, Set<String> alreadyUnlocked) {
        if (!condition) return List.of();
        if (alreadyUnlocked.contains(def.name())) return List.of();
        return doUnlock(user, def);
    }

    private List<AchievementDef> tryUnlock(AppUser user, AchievementDef def, boolean condition) {
        if (!condition) return List.of();
        if (achievementRepo.existsByUserIdAndAchievementKey(user.getId(), def.name())) {
            return List.of();
        }
        return doUnlock(user, def);
    }

    private List<AchievementDef> doUnlock(AppUser user, AchievementDef def) {

        achievementRepo.save(UserAchievement.builder()
                .user(user)
                .achievementKey(def.name())
                .build());

        eventRepo.save(GameEventLog.builder()
                .user(user)
                .eventType("ACHIEVEMENT")
                .eventData("{\"key\":\"" + def.name() + "\",\"name\":\"" + def.getDisplayName() + "\",\"exp\":" + def.getExpReward() + "}")
                .build());

        log.info("[遊戲化] 用戶 {} 解鎖成就: {} ({})", user.getId(), def.name(), def.getDisplayName());

        if (def.getExpReward() > 0) {
            awardExp(user, def.getExpReward(), "ACHIEVEMENT_" + def.name());
        }

        return List.of(def);
    }

    private List<String> checkLevelAchievements(AppUser user, int newLevel) {
        List<String> unlocked = new ArrayList<>();
        if (newLevel >= 5) tryUnlock(user, AchievementDef.LEVEL_5, true).forEach(d -> unlocked.add(d.name()));
        if (newLevel >= 10) tryUnlock(user, AchievementDef.LEVEL_10, true).forEach(d -> unlocked.add(d.name()));
        if (newLevel >= 25) tryUnlock(user, AchievementDef.LEVEL_25, true).forEach(d -> unlocked.add(d.name()));
        if (newLevel >= 50) tryUnlock(user, AchievementDef.LEVEL_50, true).forEach(d -> unlocked.add(d.name()));
        return unlocked;
    }

    public record LeaderboardEntry(String displayName, int level, long experience, String characterClass, String avatarUrl) {}
}
