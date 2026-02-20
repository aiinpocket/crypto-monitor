package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.EquipmentTemplate;
import com.aiinpocket.btctrade.model.entity.Monster;
import com.aiinpocket.btctrade.model.entity.MonsterDrop;
import com.aiinpocket.btctrade.model.enums.*;
import com.aiinpocket.btctrade.repository.EquipmentTemplateRepository;
import com.aiinpocket.btctrade.repository.MonsterDropRepository;
import com.aiinpocket.btctrade.repository.MonsterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 啟動時初始化怪物和裝備種子資料。
 * P1 階段先建立 8 隻怪物（每個風險等級 2 隻）和基本裝備。
 * 只在資料庫為空時初始化（防止重複建立）。
 */
@Component
@Order(200)  // 在 DefaultSymbolInitializer 之後執行
@RequiredArgsConstructor
@Slf4j
public class MonsterDataInitializer implements ApplicationRunner {

    private final MonsterRepository monsterRepo;
    private final EquipmentTemplateRepository equipRepo;
    private final MonsterDropRepository dropRepo;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (monsterRepo.count() > 0) {
            log.info("[怪物初始化] 已有 {} 隻怪物，跳過初始化", monsterRepo.count());
            return;
        }

        log.info("[怪物初始化] 開始建立初版 8 隻怪物和裝備...");

        // === 建立裝備模板 ===
        List<EquipmentTemplate> equipment = createEquipment();

        // === 建立 8 隻怪物 ===
        List<Monster> monsters = createMonsters();

        // === 建立掉落表 ===
        createDropTables(monsters, equipment);

        log.info("[怪物初始化] 完成！{} 隻怪物, {} 件裝備", monsters.size(), equipment.size());
    }

    private List<Monster> createMonsters() {
        List<Monster> monsters = List.of(
                // === LOW 風險（波動率 0-2%）===
                Monster.builder()
                        .name("泡沫史萊姆")
                        .description("在低波動市場中生成的溫和生物，適合新手冒險者")
                        .riskTier(MonsterRiskTier.LOW)
                        .level(3).hp(50).atk(8).def(5)
                        .expReward(10)
                        .minVolatility(0.0).maxVolatility(0.01)
                        .pixelCssClass("pixel-monster-slime")
                        .build(),
                Monster.builder()
                        .name("鐵鏽蝙蝠")
                        .description("盤整市場的常見居民，雖弱但群體出沒")
                        .riskTier(MonsterRiskTier.LOW)
                        .level(5).hp(70).atk(12).def(3)
                        .expReward(15)
                        .minVolatility(0.01).maxVolatility(0.02)
                        .pixelCssClass("pixel-monster-bat")
                        .build(),

                // === MEDIUM 風險（波動率 2-5%）===
                Monster.builder()
                        .name("暗影狼")
                        .description("在適度波動中出沒的獵食者，具備基本威脅性")
                        .riskTier(MonsterRiskTier.MEDIUM)
                        .level(10).hp(150).atk(25).def(15)
                        .expReward(30)
                        .minVolatility(0.02).maxVolatility(0.035)
                        .pixelCssClass("pixel-monster-wolf")
                        .build(),
                Monster.builder()
                        .name("熔岩哥布林")
                        .description("在趨勢形成時湧出的炙熱小怪，數量即是威脅")
                        .riskTier(MonsterRiskTier.MEDIUM)
                        .level(12).hp(120).atk(30).def(10)
                        .expReward(35)
                        .minVolatility(0.035).maxVolatility(0.05)
                        .pixelCssClass("pixel-monster-goblin")
                        .build(),

                // === HIGH 風險（波動率 5-10%）===
                Monster.builder()
                        .name("雷霆巨蠍")
                        .description("高波動環境的守護者，閃電般的攻擊讓人防不勝防")
                        .riskTier(MonsterRiskTier.HIGH)
                        .level(20).hp(300).atk(50).def(35)
                        .expReward(60)
                        .minVolatility(0.05).maxVolatility(0.075)
                        .pixelCssClass("pixel-monster-scorpion")
                        .build(),
                Monster.builder()
                        .name("霜鱗飛龍")
                        .description("在劇烈震盪中翱翔的冰龍，噴出的寒氣能凍結倉位")
                        .riskTier(MonsterRiskTier.HIGH)
                        .level(25).hp(400).atk(60).def(40)
                        .expReward(80)
                        .minVolatility(0.075).maxVolatility(0.1)
                        .pixelCssClass("pixel-monster-dragon")
                        .build(),

                // === EXTREME 風險（波動率 >10%）===
                Monster.builder()
                        .name("混沌魔神")
                        .description("只在極端波動中甦醒的遠古存在，市場崩盤的化身")
                        .riskTier(MonsterRiskTier.EXTREME)
                        .level(35).hp(600).atk(90).def(60)
                        .expReward(150)
                        .minVolatility(0.1).maxVolatility(0.2)
                        .pixelCssClass("pixel-monster-demon")
                        .build(),
                Monster.builder()
                        .name("虛空吞噬者")
                        .description("超極端波動的終極怪物，據說能吞噬整個市場的流動性")
                        .riskTier(MonsterRiskTier.EXTREME)
                        .level(40).hp(999).atk(120).def(80)
                        .expReward(250)
                        .minVolatility(0.2).maxVolatility(1.0)
                        .pixelCssClass("pixel-monster-void")
                        .build()
        );

        return monsterRepo.saveAll(monsters);
    }

    private List<EquipmentTemplate> createEquipment() {
        List<EquipmentTemplate> equipment = List.of(
                // === COMMON 武器 ===
                EquipmentTemplate.builder()
                        .name("生鏽短劍").description("新手冒險者的第一把武器")
                        .equipmentType(EquipmentType.WEAPON).rarity(Rarity.COMMON)
                        .classRestriction(CharacterClass.WARRIOR)
                        .dropRate(0.3).sellPrice(5L)
                        .pixelCssClass("pixel-equip-rusty-sword").build(),
                EquipmentTemplate.builder()
                        .name("學徒法杖").description("散發微光的木杖")
                        .equipmentType(EquipmentType.WEAPON).rarity(Rarity.COMMON)
                        .classRestriction(CharacterClass.MAGE)
                        .dropRate(0.3).sellPrice(5L)
                        .pixelCssClass("pixel-equip-apprentice-staff").build(),
                EquipmentTemplate.builder()
                        .name("獵人短弓").description("輕巧實用的短弓")
                        .equipmentType(EquipmentType.WEAPON).rarity(Rarity.COMMON)
                        .classRestriction(CharacterClass.RANGER)
                        .dropRate(0.3).sellPrice(5L)
                        .pixelCssClass("pixel-equip-short-bow").build(),
                EquipmentTemplate.builder()
                        .name("匕首").description("鋒利的雙刃短刀")
                        .equipmentType(EquipmentType.WEAPON).rarity(Rarity.COMMON)
                        .classRestriction(CharacterClass.ASSASSIN)
                        .dropRate(0.3).sellPrice(5L)
                        .pixelCssClass("pixel-equip-dagger").build(),

                // === COMMON 防具 ===
                EquipmentTemplate.builder()
                        .name("皮甲").description("基本的皮革防具")
                        .equipmentType(EquipmentType.ARMOR).rarity(Rarity.COMMON)
                        .dropRate(0.25).sellPrice(5L)
                        .pixelCssClass("pixel-equip-leather-armor").build(),

                // === RARE 武器 ===
                EquipmentTemplate.builder()
                        .name("蒼鋼長劍").description("由精鍛鋼鐵打造，散發藍光")
                        .equipmentType(EquipmentType.WEAPON).rarity(Rarity.RARE)
                        .classRestriction(CharacterClass.WARRIOR)
                        .dropRate(0.12).sellPrice(25L)
                        .pixelCssClass("pixel-equip-steel-sword").build(),
                EquipmentTemplate.builder()
                        .name("翡翠弓").description("精靈工匠的傑作，射程驚人")
                        .equipmentType(EquipmentType.WEAPON).rarity(Rarity.RARE)
                        .classRestriction(CharacterClass.RANGER)
                        .dropRate(0.12).sellPrice(25L)
                        .pixelCssClass("pixel-equip-jade-bow").build(),

                // === RARE 防具 ===
                EquipmentTemplate.builder()
                        .name("鎖子甲").description("環環相扣的金屬甲冑")
                        .equipmentType(EquipmentType.ARMOR).rarity(Rarity.RARE)
                        .dropRate(0.10).sellPrice(30L)
                        .pixelCssClass("pixel-equip-chain-mail").build(),

                // === EPIC 武器 ===
                EquipmentTemplate.builder()
                        .name("暗影之刃").description("吸收黑暗力量的詛咒雙刀")
                        .equipmentType(EquipmentType.WEAPON).rarity(Rarity.EPIC)
                        .classRestriction(CharacterClass.ASSASSIN)
                        .dropRate(0.05).sellPrice(80L)
                        .pixelCssClass("pixel-equip-shadow-blade").build(),
                EquipmentTemplate.builder()
                        .name("雷霆法典").description("封印雷電之力的古老典籍")
                        .equipmentType(EquipmentType.WEAPON).rarity(Rarity.EPIC)
                        .classRestriction(CharacterClass.MAGE)
                        .dropRate(0.05).sellPrice(80L)
                        .pixelCssClass("pixel-equip-thunder-tome").build(),

                // === EPIC 防具 ===
                EquipmentTemplate.builder()
                        .name("龍鱗盾").description("以霜龍鱗片鍛造的堅固盾牌")
                        .equipmentType(EquipmentType.ARMOR).rarity(Rarity.EPIC)
                        .classRestriction(CharacterClass.WARRIOR)
                        .dropRate(0.04).sellPrice(100L)
                        .pixelCssClass("pixel-equip-dragon-shield").build(),

                // === LEGENDARY ===
                EquipmentTemplate.builder()
                        .name("虛空斷裂者").description("據說能切開次元的傳說武器，唯有擊敗虛空吞噬者才能獲得")
                        .equipmentType(EquipmentType.WEAPON).rarity(Rarity.LEGENDARY)
                        .dropRate(0.02).sellPrice(500L)
                        .pixelCssClass("pixel-equip-void-breaker").build(),
                EquipmentTemplate.builder()
                        .name("永恆之鎧").description("傳說中不朽戰士遺留的聖物鎧甲")
                        .equipmentType(EquipmentType.ARMOR).rarity(Rarity.LEGENDARY)
                        .dropRate(0.015).sellPrice(500L)
                        .pixelCssClass("pixel-equip-eternal-armor").build()
        );

        return equipRepo.saveAll(equipment);
    }

    private void createDropTables(List<Monster> monsters, List<EquipmentTemplate> equipment) {
        // 低階怪物掉 COMMON
        List<EquipmentTemplate> commons = equipment.stream()
                .filter(e -> e.getRarity() == Rarity.COMMON).toList();
        List<EquipmentTemplate> rares = equipment.stream()
                .filter(e -> e.getRarity() == Rarity.RARE).toList();
        List<EquipmentTemplate> epics = equipment.stream()
                .filter(e -> e.getRarity() == Rarity.EPIC).toList();
        List<EquipmentTemplate> legendaries = equipment.stream()
                .filter(e -> e.getRarity() == Rarity.LEGENDARY).toList();

        for (Monster m : monsters) {
            // 所有怪物都能掉 COMMON
            for (EquipmentTemplate eq : commons) {
                dropRepo.save(MonsterDrop.builder().monster(m).equipmentTemplate(eq).build());
            }

            // MEDIUM+ 可掉 RARE
            if (m.getRiskTier().ordinal() >= MonsterRiskTier.MEDIUM.ordinal()) {
                for (EquipmentTemplate eq : rares) {
                    dropRepo.save(MonsterDrop.builder().monster(m).equipmentTemplate(eq).build());
                }
            }

            // HIGH+ 可掉 EPIC
            if (m.getRiskTier().ordinal() >= MonsterRiskTier.HIGH.ordinal()) {
                for (EquipmentTemplate eq : epics) {
                    dropRepo.save(MonsterDrop.builder().monster(m).equipmentTemplate(eq).build());
                }
            }

            // EXTREME 可掉 LEGENDARY
            if (m.getRiskTier() == MonsterRiskTier.EXTREME) {
                for (EquipmentTemplate eq : legendaries) {
                    dropRepo.save(MonsterDrop.builder().monster(m).equipmentTemplate(eq).build());
                }
            }
        }
    }
}
