package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.Species;
import com.aiinpocket.btctrade.repository.SpeciesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 啟動時初始化 16 種族種子資料。
 * 靈感來源：No Game No Life 超 16 種族 + 奇幻 RPG 經典種族。
 * 每種族搭配 2 性別 × 4 職業 = 8 種外觀組合，全 16 種族共 128 種。
 */
@Component
@Order(210)
@RequiredArgsConstructor
@Slf4j
public class SpeciesDataInitializer implements ApplicationRunner {

    private final SpeciesRepository speciesRepo;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long count = speciesRepo.count();

        if (count == 0) {
            log.info("[種族初始化] 空資料庫，建立全部 16 種族...");
            speciesRepo.saveAll(createAllSpecies());
            log.info("[種族初始化] 完成！16 種族已建立");
            return;
        }

        if (count < 16) {
            log.info("[種族初始化] 現有 {} 種族，增量新增至 16 種...", count);
            Set<String> existing = speciesRepo.findAll().stream()
                    .map(Species::getName).collect(Collectors.toSet());
            List<Species> missing = createAllSpecies().stream()
                    .filter(s -> !existing.contains(s.getName()))
                    .toList();
            if (!missing.isEmpty()) {
                speciesRepo.saveAll(missing);
                log.info("[種族初始化] 新增 {} 種族", missing.size());
            }
        } else {
            log.info("[種族初始化] 已有 {} 種族，跳過", count);
        }
    }

    private List<Species> createAllSpecies() {
        return List.of(
            // Tier 1: 初始可用（Lv.0）
            sp("人類",     "適應力最強的種族，在任何職業都能發揮穩定實力", "human",      0),
            sp("精靈",     "長壽而優雅的森林之民，天生親近自然與魔法",   "elf",        0),

            // Tier 2: 初期解鎖（Lv.3~7）
            sp("矮人",     "身材矮小卻力大無窮的山岳鍛造師",           "dwarf",      3),
            sp("獸人",     "崇尚力量的草原戰士，野性本能敏銳",          "orc",        5),
            sp("妖精",     "體型嬌小但魔力驚人的精靈近親",              "fairy",      7),

            // Tier 3: 中期解鎖（Lv.10~15）
            sp("龍裔",     "流淌著龍血的古老種族，吐息如火",            "dragonborn", 10),
            sp("半靈",     "靈界與現世的混血兒，能感知無形之力",         "halfspirit", 12),
            sp("海族",     "統治深海王國的水棲種族",                    "merfolk",    15),

            // Tier 4: 後期解鎖（Lv.18~25）
            sp("天使",     "自天界降臨的光之使者，守護正義",            "angel",      18),
            sp("惡魔",     "深淵之子，操控暗影與混沌的力量",            "demon",      20),
            sp("機關族",   "以齒輪和蒸氣驅動的自律型人偶",              "automaton",  22),
            sp("吸血鬼",   "永生不死的夜之貴族，渴望鮮血",              "vampire",    25),

            // Tier 5: 高階解鎖（Lv.28~35）
            sp("幽鬼",     "徘徊於生死之間的靈體種族",                  "phantom",    28),
            sp("巨人",     "身軀如山的遠古巨族，力能開天",              "giant",      30),

            // Tier 6: 終極解鎖（Lv.35~40）
            sp("古龍",     "超越龍裔的純血龍族，智慧與力量的頂峰",      "elderdragon",35),
            sp("神族",     "傳說中的最高種族，擁有改寫規則的能力",       "divine",     40)
        );
    }

    private static Species sp(String name, String desc, String cssPrefix, int unlockLevel) {
        return Species.builder()
                .name(name)
                .description(desc)
                .pixelCssPrefix(cssPrefix)
                .unlockLevel(unlockLevel)
                .build();
    }
}
