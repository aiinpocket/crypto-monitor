package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.model.entity.PartyMember;
import com.aiinpocket.btctrade.model.entity.Species;
import com.aiinpocket.btctrade.model.enums.CharacterClass;
import com.aiinpocket.btctrade.model.enums.Gender;
import com.aiinpocket.btctrade.repository.AppUserRepository;
import com.aiinpocket.btctrade.repository.PartyMemberRepository;
import com.aiinpocket.btctrade.repository.SpeciesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 隊伍同伴管理服務。
 * 提供招募、解散、重新排序等核心功能。
 * 同伴欄位透過等級自動解鎖（Lv.5→2人, Lv.15→3人, Lv.30→4人）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartyService {

    private final PartyMemberRepository memberRepo;
    private final SpeciesRepository speciesRepo;
    private final AppUserRepository userRepo;

    /** 隊伍欄位解鎖等級門檻 */
    private static final Map<Integer, Integer> SLOT_UNLOCK_LEVELS = Map.of(
            2, 5,   // Lv.5 → 第2人
            3, 15,  // Lv.15 → 第3人
            4, 30   // Lv.30 → 第4人
    );

    /**
     * 取得用戶的所有隊伍成員（依 slot 排序）。
     */
    public List<PartyMember> getPartyMembers(Long userId) {
        return memberRepo.findByUserIdOrderBySlot(userId);
    }

    /**
     * 取得用戶可解鎖的種族列表（等級夠高的）。
     */
    public List<Species> getAvailableSpecies(int userLevel) {
        return speciesRepo.findByUnlockLevelLessThanEqual(userLevel);
    }

    /**
     * 取得所有種族（含未解鎖的）。
     */
    public List<Species> getAllSpecies() {
        return speciesRepo.findAll();
    }

    /**
     * 計算用戶最大隊伍人數。
     */
    public int getMaxPartySize(int userLevel) {
        int max = 1;
        for (var entry : SLOT_UNLOCK_LEVELS.entrySet()) {
            if (userLevel >= entry.getValue()) {
                max = Math.max(max, entry.getKey());
            }
        }
        return max;
    }

    /**
     * 招募新同伴到隊伍。
     */
    @Transactional
    public PartyMember recruit(AppUser user, Long speciesId, Gender gender,
                                CharacterClass characterClass, String name) {
        // 檢查隊伍人數上限
        int maxSize = getMaxPartySize(user.getLevel());
        long currentSize = memberRepo.countByUserIdAndActiveTrue(user.getId());
        if (currentSize >= maxSize) {
            throw new IllegalStateException("隊伍已滿（" + currentSize + "/" + maxSize + "），需要更高等級解鎖更多欄位");
        }

        // 檢查種族是否已解鎖
        Species species = speciesRepo.findById(speciesId)
                .orElseThrow(() -> new IllegalArgumentException("找不到種族: " + speciesId));
        if (user.getLevel() < species.getUnlockLevel()) {
            throw new IllegalStateException("需要 Lv." + species.getUnlockLevel() + " 才能解鎖「" + species.getName() + "」");
        }

        // 驗證名稱
        if (name == null || name.isBlank() || name.length() > 30) {
            throw new IllegalArgumentException("同伴名稱需為 1~30 字");
        }

        // 找到空的 slot
        List<PartyMember> existing = memberRepo.findByUserIdOrderBySlot(user.getId());
        int nextSlot = 1;
        for (PartyMember m : existing) {
            if (m.isActive() && m.getSlot() == nextSlot) {
                nextSlot++;
            }
        }

        PartyMember member = PartyMember.builder()
                .user(user)
                .name(name.trim())
                .species(species)
                .gender(gender)
                .characterClass(characterClass)
                .slot(nextSlot)
                .active(true)
                .build();

        PartyMember saved = memberRepo.save(member);

        // 更新用戶 maxPartySize
        user.setMaxPartySize(maxSize);
        userRepo.save(user);

        log.info("[隊伍] 用戶 {} 招募同伴: {} ({} {} {})", user.getId(), name,
                species.getName(), gender, characterClass);
        return saved;
    }

    /**
     * 解散（移除）隊伍成員。
     */
    @Transactional
    public void dismiss(Long userId, Long memberId) {
        PartyMember member = memberRepo.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("找不到同伴: " + memberId));
        if (!member.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("無權操作此同伴");
        }
        member.setActive(false);
        memberRepo.save(member);
        log.info("[隊伍] 用戶 {} 解散同伴: {} (slot {})", userId, member.getName(), member.getSlot());
    }

    /**
     * 取得組合 CSS 類名。
     * 格式: pixel-race-{cssPrefix}
     */
    public static String getCssClass(Species species) {
        return "pixel-race-" + species.getPixelCssPrefix();
    }
}
