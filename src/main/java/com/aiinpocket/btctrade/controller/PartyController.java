package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.entity.EquipmentTemplate;
import com.aiinpocket.btctrade.model.entity.PartyMember;
import com.aiinpocket.btctrade.model.entity.Species;
import com.aiinpocket.btctrade.model.entity.UserEquipment;
import com.aiinpocket.btctrade.model.enums.CharacterClass;
import com.aiinpocket.btctrade.model.enums.EquipmentType;
import com.aiinpocket.btctrade.model.enums.Gender;
import com.aiinpocket.btctrade.repository.UserEquipmentRepository;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.PartyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 隊伍管理 REST API。
 * 提供隊伍成員 CRUD、種族查詢、欄位解鎖資訊。
 */
@RestController
@RequestMapping("/api/user/party")
@RequiredArgsConstructor
@Slf4j
public class PartyController {

    private final PartyService partyService;
    private final UserEquipmentRepository userEquipRepo;

    /** 取得隊伍資訊（成員 + 解鎖狀態 + 裝備視覺化） */
    @GetMapping
    public Map<String, Object> getParty(@AuthenticationPrincipal AppUserPrincipal principal) {
        var user = principal.getAppUser();
        List<PartyMember> members = partyService.getPartyMembers(user.getId());
        int maxSize = partyService.getMaxPartySize(user.getLevel());

        // 批次查詢所有隊員的裝備（一次查詢避免 N+1）
        List<PartyMember> activeMembers = members.stream().filter(PartyMember::isActive).toList();
        List<Long> memberIds = activeMembers.stream().map(PartyMember::getId).toList();
        Map<Long, MemberEquipInfo> equipMap = buildMemberEquipMap(memberIds);

        Map<String, Object> result = new HashMap<>();
        result.put("maxSlots", maxSize);
        result.put("userLevel", user.getLevel());
        result.put("members", activeMembers.stream()
                .map(m -> toDto(m, equipMap.getOrDefault(m.getId(), MemberEquipInfo.EMPTY)))
                .toList());
        return result;
    }

    /** 取得所有種族（含解鎖資訊） */
    @GetMapping("/species")
    public List<Map<String, Object>> getSpecies(@AuthenticationPrincipal AppUserPrincipal principal) {
        int userLevel = principal.getAppUser().getLevel();
        return partyService.getAllSpecies().stream()
                .map(s -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("id", s.getId());
                    dto.put("name", s.getName());
                    dto.put("description", s.getDescription());
                    dto.put("pixelCssClass", PartyService.getCssClass(s));
                    dto.put("unlockLevel", s.getUnlockLevel());
                    dto.put("unlocked", userLevel >= s.getUnlockLevel());
                    return dto;
                })
                .toList();
    }

    /** 招募新同伴 */
    @PostMapping("/recruit")
    public ResponseEntity<?> recruit(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        try {
            Long speciesId = ((Number) body.get("speciesId")).longValue();
            Gender gender = Gender.valueOf((String) body.get("gender"));
            CharacterClass charClass = CharacterClass.valueOf((String) body.get("characterClass"));
            String name = (String) body.get("name");

            PartyMember member = partyService.recruit(
                    principal.getAppUser(), speciesId, gender, charClass, name);
            return ResponseEntity.ok(toDto(member, MemberEquipInfo.EMPTY));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[隊伍API] 招募失敗", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "招募失敗，請稍後重試"));
        }
    }

    /** 解散隊伍成員 */
    @DeleteMapping("/{memberId}")
    public ResponseEntity<?> dismiss(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long memberId) {
        try {
            partyService.dismiss(principal.getAppUser().getId(), memberId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> toDto(PartyMember m, MemberEquipInfo equip) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", m.getId());
        dto.put("name", m.getName());
        dto.put("slot", m.getSlot());
        dto.put("gender", m.getGender().name());
        dto.put("characterClass", m.getCharacterClass().name());
        dto.put("speciesName", m.getSpecies().getName());
        dto.put("speciesId", m.getSpecies().getId());
        dto.put("pixelCssClass", PartyService.getCssClass(m.getSpecies()));
        dto.put("unlockedAt", m.getUnlockedAt() != null ? m.getUnlockedAt().toString() : null);
        // 裝備視覺化
        dto.put("equippedWeaponCss", equip.weaponCss());
        dto.put("equippedArmorCss", equip.armorCss());
        dto.put("equippedWeaponName", equip.weaponName());
        dto.put("equippedArmorName", equip.armorName());
        dto.put("equippedWeaponRarity", equip.weaponRarity());
        dto.put("equippedArmorRarity", equip.armorRarity());
        return dto;
    }

    /**
     * 批次建立隊員裝備映射（memberId → 武器/防具 CSS）。
     */
    private Map<Long, MemberEquipInfo> buildMemberEquipMap(List<Long> memberIds) {
        if (memberIds.isEmpty()) return Map.of();
        List<UserEquipment> allEquipped = userEquipRepo.findByEquippedByMemberIdIn(memberIds);
        return allEquipped.stream()
                .collect(Collectors.groupingBy(
                        ue -> ue.getEquippedByMember().getId(),
                        Collectors.collectingAndThen(Collectors.toList(), items -> {
                            String weaponCss = null, armorCss = null;
                            String weaponName = null, armorName = null;
                            String weaponRarity = null, armorRarity = null;
                            for (UserEquipment ue : items) {
                                EquipmentTemplate t = ue.getEquipmentTemplate();
                                if (t.getEquipmentType() == EquipmentType.WEAPON) {
                                    weaponCss = t.getPixelCssClass();
                                    weaponName = t.getName();
                                    weaponRarity = t.getRarity().name();
                                } else if (t.getEquipmentType() == EquipmentType.ARMOR) {
                                    armorCss = t.getPixelCssClass();
                                    armorName = t.getName();
                                    armorRarity = t.getRarity().name();
                                }
                            }
                            return new MemberEquipInfo(weaponCss, weaponName, weaponRarity, armorCss, armorName, armorRarity);
                        })
                ));
    }

    record MemberEquipInfo(String weaponCss, String weaponName, String weaponRarity,
                           String armorCss, String armorName, String armorRarity) {
        static final MemberEquipInfo EMPTY = new MemberEquipInfo(null, null, null, null, null, null);
    }
}
