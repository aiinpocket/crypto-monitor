package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.*;
import com.aiinpocket.btctrade.model.enums.BacktestRunStatus;
import com.aiinpocket.btctrade.model.enums.Rarity;
import com.aiinpocket.btctrade.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 回測冒險系統服務。
 * 負責在回測執行期間生成冒險事件（怪物遭遇 + 寶箱），
 * 並在回測完成後根據結果計算獎勵。
 *
 * <p>核心原則：所有獎勵由後端決定，前端只負責展示動畫。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestAdventureService {

    private final MonsterRepository monsterRepo;
    private final EquipmentTemplateRepository equipmentTemplateRepo;
    private final UserEquipmentRepository userEquipRepo;
    private final AppUserRepository userRepo;
    private final BacktestRunRepository backtestRunRepo;
    private final GamificationService gamificationService;
    private final BattleService battleService;
    private final EquipmentService equipmentService;
    private final ObjectMapper objectMapper;

    /** 冒險事件觸發的進度里程碑 */
    private static final int[] MILESTONES = {15, 30, 50, 70, 85};

    /**
     * 生成冒險計畫。回測提交時呼叫。
     * 從怪物圖鑑中隨機挑選怪物，分配到進度里程碑。
     *
     * @return 冒險計畫 JSON 字串
     */
    public String generateAdventurePlan() {
        List<Monster> allMonsters = monsterRepo.findAll();
        if (allMonsters.isEmpty()) {
            return "{}";
        }

        List<Map<String, Object>> events = new ArrayList<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 在每個里程碑放置事件（怪物或寶箱）
        for (int pct : MILESTONES) {
            if (rng.nextDouble() < 0.75) {
                // 75% 機率出怪物
                Monster m = allMonsters.get(rng.nextInt(allMonsters.size()));
                events.add(Map.of(
                        "pct", pct,
                        "type", "MONSTER",
                        "monsterId", m.getId(),
                        "monsterName", m.getName(),
                        "monsterLevel", m.getLevel(),
                        "monsterCss", m.getPixelCssClass() != null ? m.getPixelCssClass() : "",
                        "riskTier", m.getRiskTier().name()
                ));
            } else {
                // 25% 機率出寶箱（金幣獎勵）
                long goldAmount = (rng.nextInt(5) + 1) * 10L; // 10~50 G
                events.add(Map.of(
                        "pct", pct,
                        "type", "TREASURE",
                        "goldAmount", goldAmount
                ));
            }
        }

        try {
            Map<String, Object> plan = new HashMap<>();
            plan.put("events", events);
            plan.put("generatedAt", System.currentTimeMillis());
            return objectMapper.writeValueAsString(plan);
        } catch (Exception e) {
            log.error("[冒險] 生成冒險計畫失敗", e);
            return "{}";
        }
    }

    /**
     * 領取冒險獎勵。回測完成後由用戶觸發（一次性）。
     * 根據回測結果決定勝敗和獎勵。
     *
     * @param runId  回測 ID
     * @param userId 用戶 ID
     * @return 獎勵結果 Map（包含 EXP, gold, equipment, battles）
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> claimRewards(Long runId, Long userId) {
        BacktestRun run = backtestRunRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("回測不存在"));

        if (!run.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("無權操作此回測");
        }
        if (run.getStatus() != BacktestRunStatus.COMPLETED) {
            throw new IllegalStateException("回測尚未完成");
        }
        if (run.isAdventureRewardsClaimed()) {
            throw new IllegalStateException("獎勵已領取");
        }

        AppUser user = run.getUser();

        // 解析冒險計畫
        List<Map<String, Object>> events;
        try {
            Map<String, Object> plan = objectMapper.readValue(run.getAdventureJson(), Map.class);
            events = (List<Map<String, Object>>) plan.get("events");
        } catch (Exception e) {
            events = List.of();
        }

        // 解析回測結果判斷勝敗
        boolean profitable = false;
        double annualReturn = 0;
        try {
            Map<String, Object> result = objectMapper.readValue(run.getResultJson(), Map.class);
            Object passed = result.get("passed");
            profitable = Boolean.TRUE.equals(passed);
            Object ar = result.get("annualizedReturn");
            if (ar instanceof Number) annualReturn = ((Number) ar).doubleValue();
        } catch (Exception ignored) {}

        // 計算各怪物戰鬥結果
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int totalExp = 0;
        long totalGold = 0;
        List<Map<String, Object>> battleResults = new ArrayList<>();
        List<Map<String, Object>> droppedEquipment = new ArrayList<>();
        List<Long> discoveredMonsterIds = new ArrayList<>();

        // 勝率基於回測結果：獲利 60~80%，虧損 20~40%
        double winRate = profitable
                ? 0.6 + Math.min(annualReturn * 0.5, 0.2)
                : 0.2 + rng.nextDouble() * 0.2;

        for (Map<String, Object> event : events) {
            String type = (String) event.get("type");

            if ("MONSTER".equals(type)) {
                boolean victory = rng.nextDouble() < winRate;
                String monsterName = (String) event.get("monsterName");
                int monsterLevel = ((Number) event.get("monsterLevel")).intValue();

                // 收集怪物 ID（事務提交後再記錄圖鑑發現）
                Object monsterIdObj = event.get("monsterId");
                if (monsterIdObj instanceof Number) {
                    discoveredMonsterIds.add(((Number) monsterIdObj).longValue());
                }

                if (victory) {
                    int exp = monsterLevel * 3;
                    long gold = (long) monsterLevel * 8;
                    totalExp += exp;
                    totalGold += gold;
                    battleResults.add(Map.of(
                            "monsterName", monsterName,
                            "monsterLevel", monsterLevel,
                            "monsterCss", event.getOrDefault("monsterCss", ""),
                            "victory", true,
                            "exp", exp,
                            "gold", gold
                    ));

                    // 裝備掉落（勝利時 15% 機率）
                    if (rng.nextDouble() < 0.15) {
                        Map<String, Object> drop = rollBacktestDrop(user);
                        if (drop != null) {
                            droppedEquipment.add(drop);
                        }
                    }
                } else {
                    long penalty = Math.max((long) (monsterLevel * 2), 5);
                    battleResults.add(Map.of(
                            "monsterName", monsterName,
                            "monsterLevel", monsterLevel,
                            "monsterCss", event.getOrDefault("monsterCss", ""),
                            "victory", false,
                            "goldLost", penalty
                    ));
                }
            } else if ("TREASURE".equals(type)) {
                long goldAmount = ((Number) event.get("goldAmount")).longValue();
                totalGold += goldAmount;
                battleResults.add(Map.of(
                        "type", "TREASURE",
                        "gold", goldAmount
                ));
            }
        }

        // 發放獎勵
        user.setGameCurrency(user.getGameCurrency() + totalGold);
        userRepo.save(user);

        if (totalExp > 0) {
            gamificationService.awardExp(user, totalExp, "BACKTEST_ADVENTURE");
        }

        // 標記已領取
        run.setAdventureRewardsClaimed(true);
        backtestRunRepo.save(run);

        Map<String, Object> rewards = new HashMap<>();
        rewards.put("exp", totalExp);
        rewards.put("gold", totalGold);
        rewards.put("battles", battleResults);
        rewards.put("equipment", droppedEquipment);
        rewards.put("profitable", profitable);

        log.info("[冒險] 用戶 {} 領取回測冒險獎勵: +{} EXP, +{} G, {} 件裝備",
                userId, totalExp, totalGold, droppedEquipment.size());

        // 將遭遇的怪物 ID 放入結果，由 controller 層在事務外記錄圖鑑
        rewards.put("_discoveredMonsterIds", discoveredMonsterIds);

        return rewards;
    }

    /**
     * 回測掉落裝備判定。
     */
    private Map<String, Object> rollBacktestDrop(AppUser user) {
        List<EquipmentTemplate> allEquip = equipmentTemplateRepo.findAll();
        if (allEquip.isEmpty()) return null;

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 依稀有度加權選擇
        EquipmentTemplate selected = null;
        for (int i = 0; i < 3; i++) { // 最多嘗試 3 次
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
        if (selected == null) return null;

        // 檢查背包容量
        long currentItems = userEquipRepo.countByUserId(user.getId());
        if (currentItems >= user.getInventorySlots()) {
            return Map.of("name", selected.getName(), "rarity", selected.getRarity().name(),
                    "dropped", false, "reason", "背包已滿");
        }

        UserEquipment item = equipmentService.createWithRolledStats(user, selected, null);

        return Map.of(
                "name", selected.getName(),
                "rarity", selected.getRarity().name(),
                "type", selected.getEquipmentType().name(),
                "dropped", true,
                "totalPower", item.getTotalPower()
        );
    }
}
