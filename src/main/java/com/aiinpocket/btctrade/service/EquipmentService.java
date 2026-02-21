package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.model.entity.EquipmentTemplate;
import com.aiinpocket.btctrade.model.entity.PartyMember;
import com.aiinpocket.btctrade.model.entity.UserEquipment;
import com.aiinpocket.btctrade.model.enums.EquipmentType;
import com.aiinpocket.btctrade.repository.AppUserRepository;
import com.aiinpocket.btctrade.repository.PartyMemberRepository;
import com.aiinpocket.btctrade.repository.UserEquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 裝備管理服務。
 * 負責背包查詢、穿脫裝備、賣出裝備、擴充背包。
 * 支援主角和隊伍成員裝備。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EquipmentService {

    private final UserEquipmentRepository userEquipRepo;
    private final AppUserRepository userRepo;
    private final PartyMemberRepository memberRepo;

    // 擴充背包每次增加的格數
    private static final int EXPAND_SLOTS = 5;
    // 擴充背包費用（每次遞增）
    private static final long EXPAND_BASE_COST = 50L;
    private static final long EXPAND_COST_INCREMENT = 25L;

    /**
     * 取得用戶所有裝備（按取得時間倒序）。
     */
    public List<UserEquipment> getUserInventory(Long userId) {
        return userEquipRepo.findByUserIdOrderByAcquiredAtDesc(userId);
    }

    /**
     * 取得用戶角色身上的裝備。
     */
    public List<UserEquipment> getEquippedItems(Long userId) {
        return userEquipRepo.findByUserIdAndEquippedByUserTrue(userId);
    }

    /**
     * 裝備到角色身上。
     * 同類型只能裝一件（自動卸下原有裝備）。
     */
    @Transactional
    public EquipResult equipItem(Long userId, Long equipmentId) {
        Optional<UserEquipment> opt = userEquipRepo.findByIdAndUserId(equipmentId, userId);
        if (opt.isEmpty()) {
            return EquipResult.fail("找不到該裝備");
        }

        UserEquipment item = opt.get();
        if (Boolean.TRUE.equals(item.getEquippedByUser())) {
            return EquipResult.fail("此裝備已穿戴中");
        }

        EquipmentTemplate template = item.getEquipmentTemplate();

        // 檢查職業限制
        AppUser user = item.getUser();
        if (template.getClassRestriction() != null) {
            String userClass = user.getCharacterClass();
            if (!template.getClassRestriction().name().equals(userClass)) {
                return EquipResult.fail("此裝備限定 " + template.getClassRestriction().name() + " 職業");
            }
        }

        // 卸下同類型已裝備的裝備
        EquipmentType type = template.getEquipmentType();
        Optional<UserEquipment> currentEquipped = userEquipRepo.findEquippedByUserAndType(userId, type);
        if (currentEquipped.isPresent()) {
            UserEquipment old = currentEquipped.get();
            old.setEquippedByUser(false);
            userEquipRepo.save(old);
        }

        // 裝備新裝備
        item.setEquippedByUser(true);
        userEquipRepo.save(item);

        log.info("[裝備] 用戶 {} 裝備了「{}」({})", userId, template.getName(), type);
        return EquipResult.success("已裝備「" + template.getName() + "」");
    }

    /**
     * 裝備到隊伍成員身上。
     * 同類型只能裝一件（自動卸下原有裝備）。
     */
    @Transactional
    public EquipResult equipItemToMember(Long userId, Long equipmentId, Long memberId) {
        Optional<UserEquipment> opt = userEquipRepo.findByIdAndUserId(equipmentId, userId);
        if (opt.isEmpty()) {
            return EquipResult.fail("找不到該裝備");
        }

        UserEquipment item = opt.get();
        if (Boolean.TRUE.equals(item.getEquippedByUser()) || item.getEquippedByMember() != null) {
            return EquipResult.fail("此裝備已穿戴中，請先卸下");
        }

        // 確認隊伍成員屬於該用戶且啟用中
        PartyMember member = memberRepo.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("找不到隊伍成員"));
        if (!member.getUser().getId().equals(userId) || !member.isActive()) {
            return EquipResult.fail("無效的隊伍成員");
        }

        EquipmentTemplate template = item.getEquipmentTemplate();

        // 檢查職業限制（對應隊員的職業）
        if (template.getClassRestriction() != null) {
            if (!template.getClassRestriction().name().equals(member.getCharacterClass().name())) {
                return EquipResult.fail("此裝備限定 " + template.getClassRestriction().name() + " 職業");
            }
        }

        // 卸下隊員同類型已裝備的裝備
        EquipmentType type = template.getEquipmentType();
        Optional<UserEquipment> currentEquipped = userEquipRepo.findEquippedByMemberAndType(memberId, type);
        if (currentEquipped.isPresent()) {
            UserEquipment old = currentEquipped.get();
            old.setEquippedByMember(null);
            userEquipRepo.save(old);
        }

        // 裝備到隊員
        item.setEquippedByMember(member);
        userEquipRepo.save(item);

        log.info("[裝備] 用戶 {} 把「{}」裝備給隊員「{}」({})",
                userId, template.getName(), member.getName(), type);
        return EquipResult.success("已將「" + template.getName() + "」裝備給「" + member.getName() + "」");
    }

    /**
     * 卸下裝備（支援主角和隊伍成員）。
     */
    @Transactional
    public EquipResult unequipItem(Long userId, Long equipmentId) {
        Optional<UserEquipment> opt = userEquipRepo.findByIdAndUserId(equipmentId, userId);
        if (opt.isEmpty()) {
            return EquipResult.fail("找不到該裝備");
        }

        UserEquipment item = opt.get();
        String itemName = item.getEquipmentTemplate().getName();

        if (Boolean.TRUE.equals(item.getEquippedByUser())) {
            item.setEquippedByUser(false);
            userEquipRepo.save(item);
            log.info("[裝備] 用戶 {} 卸下了「{}」", userId, itemName);
            return EquipResult.success("已卸下「" + itemName + "」");
        }

        if (item.getEquippedByMember() != null) {
            String memberName = item.getEquippedByMember().getName();
            item.setEquippedByMember(null);
            userEquipRepo.save(item);
            log.info("[裝備] 用戶 {} 從隊員「{}」卸下了「{}」", userId, memberName, itemName);
            return EquipResult.success("已從「" + memberName + "」卸下「" + itemName + "」");
        }

        return EquipResult.fail("此裝備未穿戴");
    }

    /**
     * 賣出裝備換遊戲幣。
     */
    @Transactional
    public EquipResult sellItem(Long userId, Long equipmentId) {
        Optional<UserEquipment> opt = userEquipRepo.findByIdAndUserId(equipmentId, userId);
        if (opt.isEmpty()) {
            return EquipResult.fail("找不到該裝備");
        }

        UserEquipment item = opt.get();
        if (Boolean.TRUE.equals(item.getEquippedByUser()) || item.getEquippedByMember() != null) {
            return EquipResult.fail("請先卸下裝備再賣出");
        }

        EquipmentTemplate template = item.getEquipmentTemplate();
        long sellPrice = template.getSellPrice();

        // 增加用戶金幣
        AppUser user = userRepo.findById(userId).orElseThrow();
        user.setGameCurrency(user.getGameCurrency() + sellPrice);
        userRepo.save(user);

        // 刪除裝備
        userEquipRepo.delete(item);

        log.info("[裝備] 用戶 {} 賣出「{}」獲得 {} 金幣", userId, template.getName(), sellPrice);
        return EquipResult.success("賣出「" + template.getName() + "」獲得 " + sellPrice + " G",
                sellPrice, user.getGameCurrency());
    }

    /**
     * 擴充背包格數。
     */
    @Transactional
    public EquipResult expandInventory(Long userId) {
        AppUser user = userRepo.findById(userId).orElseThrow();

        int currentSlots = user.getInventorySlots();
        long cost = calculateExpandCost(currentSlots);

        if (user.getGameCurrency() < cost) {
            return EquipResult.fail("金幣不足（需要 " + cost + " G，目前 " + user.getGameCurrency() + " G）");
        }

        user.setGameCurrency(user.getGameCurrency() - cost);
        user.setInventorySlots(currentSlots + EXPAND_SLOTS);
        userRepo.save(user);

        log.info("[裝備] 用戶 {} 擴充背包 {} → {} 格，花費 {} G",
                userId, currentSlots, user.getInventorySlots(), cost);
        return EquipResult.success(
                "背包擴充至 " + user.getInventorySlots() + " 格",
                -cost, user.getGameCurrency());
    }

    /**
     * 計算擴充背包費用。
     * 初始 100 格，每次擴充 +5 格，費用遞增。
     */
    public long calculateExpandCost(int currentSlots) {
        int expansionCount = (currentSlots - 100) / EXPAND_SLOTS;
        return EXPAND_BASE_COST + (long) expansionCount * EXPAND_COST_INCREMENT;
    }

    /**
     * 取得背包摘要統計。
     */
    public InventorySummary getInventorySummary(Long userId) {
        AppUser user = userRepo.findById(userId).orElseThrow();
        long itemCount = userEquipRepo.countByUserId(userId);
        List<UserEquipment> equipped = userEquipRepo.findByUserIdAndEquippedByUserTrue(userId);

        String equippedWeaponName = null;
        String equippedArmorName = null;
        String equippedWeaponCss = null;
        String equippedArmorCss = null;

        for (UserEquipment ue : equipped) {
            EquipmentTemplate t = ue.getEquipmentTemplate();
            if (t.getEquipmentType() == EquipmentType.WEAPON) {
                equippedWeaponName = t.getName();
                equippedWeaponCss = t.getPixelCssClass();
            } else if (t.getEquipmentType() == EquipmentType.ARMOR) {
                equippedArmorName = t.getName();
                equippedArmorCss = t.getPixelCssClass();
            }
        }

        return new InventorySummary(
                itemCount, user.getInventorySlots(),
                user.getGameCurrency(),
                calculateExpandCost(user.getInventorySlots()),
                equippedWeaponName, equippedArmorName,
                equippedWeaponCss, equippedArmorCss
        );
    }

    // ===== Result Records =====

    public record EquipResult(boolean success, String message, long goldChange, long currentGold) {
        public static EquipResult success(String msg) {
            return new EquipResult(true, msg, 0, 0);
        }
        public static EquipResult success(String msg, long goldChange, long currentGold) {
            return new EquipResult(true, msg, goldChange, currentGold);
        }
        public static EquipResult fail(String msg) {
            return new EquipResult(false, msg, 0, 0);
        }
    }

    public record InventorySummary(
            long itemCount, int maxSlots, long gameCurrency, long expandCost,
            String equippedWeaponName, String equippedArmorName,
            String equippedWeaponCss, String equippedArmorCss
    ) {}
}
