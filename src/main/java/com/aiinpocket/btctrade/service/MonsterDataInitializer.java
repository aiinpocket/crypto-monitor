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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 啟動時初始化怪物和裝備種子資料。
 * P1: 8 隻怪物 + 13 件裝備（fresh DB）
 * P3: 擴展到 40 隻怪物 + 21 件裝備（已有資料時增量新增）
 */
@Component
@Order(200)
@RequiredArgsConstructor
@Slf4j
public class MonsterDataInitializer implements ApplicationRunner {

    private final MonsterRepository monsterRepo;
    private final EquipmentTemplateRepository equipRepo;
    private final MonsterDropRepository dropRepo;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long monsterCount = monsterRepo.count();
        long equipCount = equipRepo.count();

        if (monsterCount == 0) {
            log.info("[怪物初始化] 空資料庫，建立全部 40 隻怪物和裝備...");
            List<EquipmentTemplate> allEquip = createAllEquipment();
            List<Monster> allMonsters = createAllMonsters();
            createDropTables(allMonsters, allEquip);
            log.info("[怪物初始化] 完成！{} 隻怪物, {} 件裝備", allMonsters.size(), allEquip.size());
            return;
        }

        // P3 擴展：已有 P1 資料，增量新增
        if (monsterCount < 40) {
            log.info("[怪物初始化] P3 擴展：現有 {} 隻怪物，新增至 40 隻...", monsterCount);
            Set<String> existingNames = monsterRepo.findAll().stream()
                    .map(Monster::getName).collect(Collectors.toSet());
            Set<String> existingEquipNames = equipRepo.findAll().stream()
                    .map(EquipmentTemplate::getName).collect(Collectors.toSet());

            // 新增缺少的裝備
            List<EquipmentTemplate> newEquip = createP3Equipment().stream()
                    .filter(e -> !existingEquipNames.contains(e.getName()))
                    .toList();
            if (!newEquip.isEmpty()) {
                equipRepo.saveAll(newEquip);
                log.info("[怪物初始化] 新增 {} 件裝備", newEquip.size());
            }

            // 新增缺少的怪物
            List<Monster> newMonsters = createP3Monsters().stream()
                    .filter(m -> !existingNames.contains(m.getName()))
                    .toList();
            if (!newMonsters.isEmpty()) {
                List<Monster> saved = monsterRepo.saveAll(newMonsters);
                // 為新怪物建立掉落表
                List<EquipmentTemplate> allEquip = equipRepo.findAll();
                createDropTables(saved, allEquip);
                log.info("[怪物初始化] 新增 {} 隻怪物", saved.size());
            }
        } else {
            log.info("[怪物初始化] 已有 {} 隻怪物，跳過", monsterCount);
        }
    }

    /** 建立全部怪物（P1 + P3） */
    private List<Monster> createAllMonsters() {
        List<Monster> all = new ArrayList<>(createP1Monsters());
        all.addAll(createP3Monsters());
        return monsterRepo.saveAll(all);
    }

    /** 建立全部裝備（P1 + P3） */
    private List<EquipmentTemplate> createAllEquipment() {
        List<EquipmentTemplate> all = new ArrayList<>(createP1Equipment());
        all.addAll(createP3Equipment());
        return equipRepo.saveAll(all);
    }

    // ==================== P1 原始 8 隻怪物 ====================

    private List<Monster> createP1Monsters() {
        return List.of(
            m("泡沫史萊姆", "在低波動市場中生成的溫和生物", MonsterRiskTier.LOW, 3, 50, 8, 5, 10, 0.0, 0.01, "pixel-monster-slime"),
            m("鐵鏽蝙蝠", "盤整市場的常見居民，群體出沒", MonsterRiskTier.LOW, 5, 70, 12, 3, 15, 0.01, 0.02, "pixel-monster-bat"),
            m("暗影狼", "在適度波動中出沒的獵食者", MonsterRiskTier.MEDIUM, 10, 150, 25, 15, 30, 0.02, 0.035, "pixel-monster-wolf"),
            m("熔岩哥布林", "在趨勢形成時湧出的炙熱小怪", MonsterRiskTier.MEDIUM, 12, 120, 30, 10, 35, 0.035, 0.05, "pixel-monster-goblin"),
            m("雷霆巨蠍", "高波動環境的守護者", MonsterRiskTier.HIGH, 20, 300, 50, 35, 60, 0.05, 0.075, "pixel-monster-scorpion"),
            m("霜鱗飛龍", "在劇烈震盪中翱翔的冰龍", MonsterRiskTier.HIGH, 25, 400, 60, 40, 80, 0.075, 0.1, "pixel-monster-dragon"),
            m("混沌魔神", "只在極端波動中甦醒的遠古存在", MonsterRiskTier.EXTREME, 35, 600, 90, 60, 150, 0.1, 0.2, "pixel-monster-demon"),
            m("虛空吞噬者", "超極端波動的終極怪物", MonsterRiskTier.EXTREME, 40, 999, 120, 80, 250, 0.2, 1.0, "pixel-monster-void")
        );
    }

    // ==================== P3 新增 32 隻怪物 ====================

    private List<Monster> createP3Monsters() {
        return List.of(
            // === LOW 新增 7 隻 (Lv 1,2,4,6,7,8,9) ===
            m("微塵蟲", "在平靜市場中飄浮的渺小生物", MonsterRiskTier.LOW, 1, 20, 3, 2, 3, 0.0, 0.003, "pixel-monster-dust"),
            m("孢子蘑菇", "低波動區域的靜態魔植物", MonsterRiskTier.LOW, 2, 30, 5, 3, 5, 0.003, 0.006, "pixel-monster-mushroom"),
            m("翠鱗蛇", "窄幅震盪中蜿蜒爬行的冷血爬蟲", MonsterRiskTier.LOW, 4, 40, 7, 4, 8, 0.006, 0.01, "pixel-monster-snake"),
            m("鐵齒鼠", "啃噬支撐位的灰色鼠群", MonsterRiskTier.LOW, 6, 55, 9, 5, 12, 0.01, 0.013, "pixel-monster-rat"),
            m("寶石甲蟲", "盤整區域的閃光甲蟲", MonsterRiskTier.LOW, 7, 60, 10, 6, 14, 0.013, 0.016, "pixel-monster-beetle"),
            m("毒霧蛙", "在價格迷霧中伏擊的紫色毒蛙", MonsterRiskTier.LOW, 8, 65, 11, 7, 16, 0.016, 0.018, "pixel-monster-frog"),
            m("淺灘螃蟹", "橫行於阻力支撐間的甲殼戰士", MonsterRiskTier.LOW, 9, 75, 13, 8, 20, 0.018, 0.02, "pixel-monster-crab"),

            // === MEDIUM 新增 8 隻 (Lv 11,13,14,15,16,17,18,19) ===
            m("裂紋石像鬼", "從破裂趨勢線中甦醒的石造魔物", MonsterRiskTier.MEDIUM, 11, 100, 18, 12, 28, 0.02, 0.025, "pixel-monster-gargoyle"),
            m("銀骨戰士", "盤整迷宮中的不死衛兵", MonsterRiskTier.MEDIUM, 13, 110, 20, 13, 38, 0.025, 0.028, "pixel-monster-skeleton"),
            m("火鱗蜥蜴", "趨勢加速時噴射火焰的巨蜥", MonsterRiskTier.MEDIUM, 14, 125, 22, 14, 42, 0.028, 0.031, "pixel-monster-lizard"),
            m("枯木樹人", "被市場波動喚醒的古老樹靈", MonsterRiskTier.MEDIUM, 15, 135, 23, 15, 48, 0.031, 0.035, "pixel-monster-treant"),
            m("鐵角牛魔", "在趨勢衝刺中橫衝直撞的蠻獸", MonsterRiskTier.MEDIUM, 16, 140, 25, 16, 55, 0.035, 0.039, "pixel-monster-minotaur"),
            m("暴風鷹女", "在波動風暴中翱翔的鳥人", MonsterRiskTier.MEDIUM, 17, 150, 27, 17, 62, 0.039, 0.043, "pixel-monster-harpy"),
            m("花崗石魔像", "被恐懼情緒凝聚的巨型石偶", MonsterRiskTier.MEDIUM, 18, 160, 28, 18, 70, 0.043, 0.047, "pixel-monster-golem"),
            m("熔融巨魔", "全身滾燙岩漿的狂暴巨魔", MonsterRiskTier.MEDIUM, 19, 170, 30, 19, 80, 0.047, 0.05, "pixel-monster-ogre"),

            // === HIGH 新增 8 隻 (Lv 21,22,23,24,26,27,28,29) ===
            m("暴跌幽靈", "由爆倉者怨念凝聚的怨靈", MonsterRiskTier.HIGH, 21, 220, 34, 23, 70, 0.05, 0.055, "pixel-monster-wraith"),
            m("深淵蛇女", "深水區域的半蛇半人魔物", MonsterRiskTier.HIGH, 22, 240, 36, 24, 85, 0.055, 0.06, "pixel-monster-naga"),
            m("毒刺飛龍", "振翅即可掀起波動風暴的毒龍", MonsterRiskTier.HIGH, 23, 260, 38, 25, 100, 0.06, 0.065, "pixel-monster-wyvern"),
            m("暴漲幻影", "模仿歷史形態的半透明幻象生物", MonsterRiskTier.HIGH, 24, 280, 40, 26, 115, 0.065, 0.075, "pixel-monster-phantom"),
            m("三頭奇美拉", "多頭混合魔獸，攻擊難以預測", MonsterRiskTier.HIGH, 26, 320, 44, 29, 130, 0.075, 0.082, "pixel-monster-chimera"),
            m("九頭蛇獸", "斬斷一頭會長出兩頭的恐怖巨蛇", MonsterRiskTier.HIGH, 27, 340, 46, 30, 148, 0.082, 0.089, "pixel-monster-hydra"),
            m("烈焰元素", "純粹火焰能量凝聚的元素精靈", MonsterRiskTier.HIGH, 28, 360, 48, 31, 168, 0.089, 0.095, "pixel-monster-elemental"),
            m("崩盤鳳凰", "每次市場崩盤都會浴火重生的神鳥", MonsterRiskTier.HIGH, 29, 380, 50, 33, 190, 0.095, 0.1, "pixel-monster-phoenix"),

            // === EXTREME 新增 9 隻 (Lv 30,31,32,33,34,36,37,38,39) ===
            m("屍骨巫王", "操控市場恐懼的不死法師", MonsterRiskTier.EXTREME, 30, 420, 56, 36, 160, 0.1, 0.12, "pixel-monster-lich"),
            m("泰坦巨人", "身軀貫穿雲層的遠古巨人", MonsterRiskTier.EXTREME, 31, 450, 60, 38, 180, 0.12, 0.14, "pixel-monster-titan"),
            m("深淵海妖", "吞噬流動性的觸手怪物", MonsterRiskTier.EXTREME, 32, 480, 64, 40, 200, 0.14, 0.16, "pixel-monster-kraken"),
            m("利維坦", "潛伏在市場深處的遠古海蛇", MonsterRiskTier.EXTREME, 33, 510, 68, 42, 220, 0.16, 0.2, "pixel-monster-leviathan"),
            m("伊弗利特", "燃燒一切的火焰巨靈", MonsterRiskTier.EXTREME, 34, 550, 72, 45, 250, 0.2, 0.3, "pixel-monster-ifrit"),
            m("巴哈姆特", "龍族之王，一擊毀滅市場", MonsterRiskTier.EXTREME, 36, 620, 80, 50, 320, 0.3, 0.45, "pixel-monster-bahamut"),
            m("魔狼芬里爾", "吞噬太陽與月亮的終末巨狼", MonsterRiskTier.EXTREME, 37, 680, 88, 55, 400, 0.45, 0.6, "pixel-monster-fenrir"),
            m("提亞馬特", "五屬性融合的混沌龍母", MonsterRiskTier.EXTREME, 38, 750, 95, 60, 500, 0.6, 0.8, "pixel-monster-tiamat"),
            m("終末之獸", "一切秩序崩壞的終極化身", MonsterRiskTier.EXTREME, 39, 850, 105, 70, 650, 0.8, 1.0, "pixel-monster-ragnarok")
        );
    }

    // ==================== P1 原始 13 件裝備 ====================

    private List<EquipmentTemplate> createP1Equipment() {
        return List.of(
            eq("生鏽短劍", "新手冒險者的第一把武器", EquipmentType.WEAPON, Rarity.COMMON, CharacterClass.WARRIOR, 0.3, 5L, "pixel-equip-rusty-sword"),
            eq("學徒法杖", "散發微光的木杖", EquipmentType.WEAPON, Rarity.COMMON, CharacterClass.MAGE, 0.3, 5L, "pixel-equip-apprentice-staff"),
            eq("獵人短弓", "輕巧實用的短弓", EquipmentType.WEAPON, Rarity.COMMON, CharacterClass.RANGER, 0.3, 5L, "pixel-equip-short-bow"),
            eq("匕首", "鋒利的雙刃短刀", EquipmentType.WEAPON, Rarity.COMMON, CharacterClass.ASSASSIN, 0.3, 5L, "pixel-equip-dagger"),
            eq("皮甲", "基本的皮革防具", EquipmentType.ARMOR, Rarity.COMMON, null, 0.25, 5L, "pixel-equip-leather-armor"),
            eq("蒼鋼長劍", "由精鍛鋼鐵打造，散發藍光", EquipmentType.WEAPON, Rarity.RARE, CharacterClass.WARRIOR, 0.12, 25L, "pixel-equip-steel-sword"),
            eq("翡翠弓", "精靈工匠的傑作，射程驚人", EquipmentType.WEAPON, Rarity.RARE, CharacterClass.RANGER, 0.12, 25L, "pixel-equip-jade-bow"),
            eq("鎖子甲", "環環相扣的金屬甲冑", EquipmentType.ARMOR, Rarity.RARE, null, 0.10, 30L, "pixel-equip-chain-mail"),
            eq("暗影之刃", "吸收黑暗力量的詛咒雙刀", EquipmentType.WEAPON, Rarity.EPIC, CharacterClass.ASSASSIN, 0.05, 80L, "pixel-equip-shadow-blade"),
            eq("雷霆法典", "封印雷電之力的古老典籍", EquipmentType.WEAPON, Rarity.EPIC, CharacterClass.MAGE, 0.05, 80L, "pixel-equip-thunder-tome"),
            eq("龍鱗盾", "以霜龍鱗片鍛造的堅固盾牌", EquipmentType.ARMOR, Rarity.EPIC, CharacterClass.WARRIOR, 0.04, 100L, "pixel-equip-dragon-shield"),
            eq("虛空斷裂者", "據說能切開次元的傳說武器", EquipmentType.WEAPON, Rarity.LEGENDARY, null, 0.02, 500L, "pixel-equip-void-breaker"),
            eq("永恆之鎧", "傳說中不朽戰士遺留的聖物鎧甲", EquipmentType.ARMOR, Rarity.LEGENDARY, null, 0.015, 500L, "pixel-equip-eternal-armor")
        );
    }

    // ==================== P3 新增 8 件裝備 ====================

    private List<EquipmentTemplate> createP3Equipment() {
        return List.of(
            eq("木盾", "新手用的圓木盾牌", EquipmentType.ARMOR, Rarity.COMMON, null, 0.25, 5L, "pixel-equip-wood-shield"),
            eq("毒蛇短刀", "淬毒的彎刀，刺客的好夥伴", EquipmentType.WEAPON, Rarity.RARE, CharacterClass.ASSASSIN, 0.10, 25L, "pixel-equip-venom-dagger"),
            eq("寒冰法典", "封印冰霜咒語的典籍", EquipmentType.WEAPON, Rarity.RARE, CharacterClass.MAGE, 0.10, 25L, "pixel-equip-ice-tome"),
            eq("獸皮護甲", "以魔獸毛皮縫製的堅韌護甲", EquipmentType.ARMOR, Rarity.RARE, null, 0.08, 35L, "pixel-equip-beast-armor"),
            eq("烈焰大劍", "灼燒一切的雙手巨劍", EquipmentType.WEAPON, Rarity.EPIC, CharacterClass.WARRIOR, 0.04, 90L, "pixel-equip-flame-sword"),
            eq("精靈長弓", "精靈王族傳承的神木長弓", EquipmentType.WEAPON, Rarity.EPIC, CharacterClass.RANGER, 0.04, 90L, "pixel-equip-elven-bow"),
            eq("暗殺斗篷", "融入暗影的神秘斗篷", EquipmentType.ARMOR, Rarity.EPIC, CharacterClass.ASSASSIN, 0.03, 100L, "pixel-equip-shadow-cloak"),
            eq("天災巨斧", "終末之力鑄造的傳說巨斧", EquipmentType.WEAPON, Rarity.LEGENDARY, null, 0.015, 600L, "pixel-equip-doom-axe")
        );
    }

    // ==================== 掉落表建立 ====================

    private void createDropTables(List<Monster> monsters, List<EquipmentTemplate> equipment) {
        List<EquipmentTemplate> commons = equipment.stream().filter(e -> e.getRarity() == Rarity.COMMON).toList();
        List<EquipmentTemplate> rares = equipment.stream().filter(e -> e.getRarity() == Rarity.RARE).toList();
        List<EquipmentTemplate> epics = equipment.stream().filter(e -> e.getRarity() == Rarity.EPIC).toList();
        List<EquipmentTemplate> legendaries = equipment.stream().filter(e -> e.getRarity() == Rarity.LEGENDARY).toList();

        for (Monster m : monsters) {
            for (EquipmentTemplate eq : commons) {
                dropRepo.save(MonsterDrop.builder().monster(m).equipmentTemplate(eq).build());
            }
            if (m.getRiskTier().ordinal() >= MonsterRiskTier.MEDIUM.ordinal()) {
                for (EquipmentTemplate eq : rares) {
                    dropRepo.save(MonsterDrop.builder().monster(m).equipmentTemplate(eq).build());
                }
            }
            if (m.getRiskTier().ordinal() >= MonsterRiskTier.HIGH.ordinal()) {
                for (EquipmentTemplate eq : epics) {
                    dropRepo.save(MonsterDrop.builder().monster(m).equipmentTemplate(eq).build());
                }
            }
            if (m.getRiskTier() == MonsterRiskTier.EXTREME) {
                for (EquipmentTemplate eq : legendaries) {
                    dropRepo.save(MonsterDrop.builder().monster(m).equipmentTemplate(eq).build());
                }
            }
        }
    }

    // ==================== Helper Methods ====================

    private static Monster m(String name, String desc, MonsterRiskTier tier, int lv, int hp, int atk, int def, int exp,
                              double minVol, double maxVol, String css) {
        return Monster.builder()
                .name(name).description(desc).riskTier(tier)
                .level(lv).hp(hp).atk(atk).def(def).expReward(exp)
                .minVolatility(minVol).maxVolatility(maxVol)
                .pixelCssClass(css).build();
    }

    private static EquipmentTemplate eq(String name, String desc, EquipmentType type, Rarity rarity,
                                         CharacterClass cls, double dropRate, Long sellPrice, String css) {
        return EquipmentTemplate.builder()
                .name(name).description(desc).equipmentType(type).rarity(rarity)
                .classRestriction(cls).dropRate(dropRate).sellPrice(sellPrice)
                .pixelCssClass(css).build();
    }
}
