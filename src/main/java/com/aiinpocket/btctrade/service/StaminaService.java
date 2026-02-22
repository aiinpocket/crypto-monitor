package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 體力系統服務。
 * 管理用戶回測體力的消耗、時間回復和金幣購買。
 *
 * <p>設計：
 * <ul>
 *   <li>maxStamina = 50</li>
 *   <li>回測消耗 = 回測年數（1年=1, 5年=5）</li>
 *   <li>每 30 分鐘自然回復 1 點</li>
 *   <li>金幣恢復：每 1 點體力 = 5 金幣</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaminaService {

    private final AppUserRepository userRepo;

    /** 每 30 分鐘回復 1 點體力 */
    private static final int REGEN_INTERVAL_MINUTES = 30;

    /** 金幣恢復價格：每 1 點體力 = 20 金幣（防止越測越有錢的無限循環） */
    private static final int GOLD_PER_STAMINA = 20;

    /**
     * 取得用戶目前的體力資訊（會自動套用時間回復）。
     */
    @Transactional
    public Map<String, Object> getStaminaInfo(AppUser user) {
        // 重新從 DB 載入，避免 session 快照覆蓋 DB 資料（如 pvpRating）
        user = userRepo.findById(user.getId()).orElseThrow();
        applyRegen(user);
        userRepo.save(user);

        int missing = user.getMaxStamina() - user.getStamina();
        long restoreCost = (long) missing * GOLD_PER_STAMINA;

        return Map.of(
                "stamina", user.getStamina(),
                "maxStamina", user.getMaxStamina(),
                "goldPerStamina", GOLD_PER_STAMINA,
                "restoreCost", restoreCost,
                "userGold", user.getGameCurrency(),
                "regenIntervalMinutes", REGEN_INTERVAL_MINUTES,
                "nextRegenAt", computeNextRegenAt(user)
        );
    }

    /**
     * 消耗體力（回測提交時呼叫）。
     *
     * @param user 用戶
     * @param cost 消耗量（= 回測年數）
     * @throws IllegalStateException 體力不足
     */
    @Transactional
    public void consumeStamina(AppUser user, int cost) {
        // 重新從 DB 載入，避免 session 快照覆蓋 DB 資料
        user = userRepo.findById(user.getId()).orElseThrow();
        applyRegen(user);
        if (user.getStamina() < cost) {
            throw new IllegalStateException(
                    String.format("體力不足！需要 %d 點，目前 %d 點", cost, user.getStamina()));
        }
        user.setStamina(user.getStamina() - cost);
        userRepo.save(user);
        log.info("[體力] 用戶 {} 消耗 {} 點體力，剩餘 {}/{}", user.getId(), cost, user.getStamina(), user.getMaxStamina());
    }

    /**
     * 使用金幣恢復體力（神父祈禱）。
     * 恢復指定數量的體力，不超過上限。
     *
     * @param user   用戶
     * @param amount 要恢復的體力點數
     * @return 恢復結果
     */
    @Transactional
    public Map<String, Object> restoreWithGold(AppUser user, int amount) {
        // 重新從 DB 載入，避免 session 快照覆蓋 DB 資料
        user = userRepo.findById(user.getId()).orElseThrow();
        applyRegen(user);

        int missing = user.getMaxStamina() - user.getStamina();
        if (missing <= 0) {
            throw new IllegalStateException("體力已滿，不需要恢復");
        }

        // 實際恢復量不超過缺少的體力
        int actualRestore = Math.min(amount, missing);
        long goldCost = (long) actualRestore * GOLD_PER_STAMINA;

        if (user.getGameCurrency() < goldCost) {
            throw new IllegalStateException(
                    String.format("金幣不足！需要 %d G，目前 %d G", goldCost, user.getGameCurrency()));
        }

        user.setGameCurrency(user.getGameCurrency() - goldCost);
        user.setStamina(user.getStamina() + actualRestore);
        userRepo.save(user);

        log.info("[體力] 用戶 {} 使用 {} G 恢復 {} 點體力 → {}/{}",
                user.getId(), goldCost, actualRestore, user.getStamina(), user.getMaxStamina());

        return Map.of(
                "restored", actualRestore,
                "goldSpent", goldCost,
                "stamina", user.getStamina(),
                "maxStamina", user.getMaxStamina(),
                "userGold", user.getGameCurrency()
        );
    }

    /**
     * 套用時間回復。
     * 根據 lastStaminaRegenAt 計算應回復的體力。
     */
    private void applyRegen(AppUser user) {
        if (user.getStamina() >= user.getMaxStamina()) {
            // 體力已滿，更新時間戳
            user.setLastStaminaRegenAt(Instant.now());
            return;
        }

        Instant lastRegen = user.getLastStaminaRegenAt();
        if (lastRegen == null) {
            user.setLastStaminaRegenAt(Instant.now());
            return;
        }

        long minutesElapsed = Duration.between(lastRegen, Instant.now()).toMinutes();
        int pointsToRegen = (int) (minutesElapsed / REGEN_INTERVAL_MINUTES);

        if (pointsToRegen > 0) {
            int newStamina = Math.min(user.getMaxStamina(), user.getStamina() + pointsToRegen);
            user.setStamina(newStamina);
            // 只前進已消耗的回復次數的時間，保留餘數
            user.setLastStaminaRegenAt(
                    lastRegen.plus(Duration.ofMinutes((long) pointsToRegen * REGEN_INTERVAL_MINUTES)));
        }
    }

    /**
     * 計算下一次體力回復的時間戳。
     */
    private String computeNextRegenAt(AppUser user) {
        if (user.getStamina() >= user.getMaxStamina()) return "";
        Instant lastRegen = user.getLastStaminaRegenAt();
        if (lastRegen == null) return "";
        return lastRegen.plus(Duration.ofMinutes(REGEN_INTERVAL_MINUTES)).toString();
    }
}
