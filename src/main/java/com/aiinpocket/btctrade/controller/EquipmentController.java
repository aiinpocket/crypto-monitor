package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.entity.PartyMember;
import com.aiinpocket.btctrade.model.entity.UserEquipment;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.EquipmentService;
import com.aiinpocket.btctrade.service.EquipmentService.EquipResult;
import com.aiinpocket.btctrade.service.EquipmentService.InventorySummary;
import com.aiinpocket.btctrade.service.PartyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 裝備系統 REST API。
 * 提供背包查詢、穿脫（主角+隊員）、賣出、擴充功能。
 */
@RestController
@RequestMapping("/api/user/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;
    private final PartyService partyService;

    /** 取得背包所有裝備 */
    @GetMapping("/inventory")
    public ResponseEntity<List<EquipmentDto>> getInventory(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        List<UserEquipment> items = equipmentService.getUserInventory(principal.getUserId());
        return ResponseEntity.ok(items.stream().map(this::toDto).toList());
    }

    /** 取得背包摘要 */
    @GetMapping("/summary")
    public ResponseEntity<InventorySummary> getSummary(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.ok(equipmentService.getInventorySummary(principal.getUserId()));
    }

    /** 取得角色已裝備的物品 */
    @GetMapping("/equipped")
    public ResponseEntity<List<EquipmentDto>> getEquipped(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        List<UserEquipment> items = equipmentService.getEquippedItems(principal.getUserId());
        return ResponseEntity.ok(items.stream().map(this::toDto).toList());
    }

    /** 取得隊伍成員列表（用於穿戴選擇） */
    @GetMapping("/party-members")
    public ResponseEntity<List<PartyMemberDto>> getPartyMembers(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        List<PartyMember> members = partyService.getPartyMembers(principal.getUserId());
        return ResponseEntity.ok(members.stream()
                .filter(PartyMember::isActive)
                .map(m -> new PartyMemberDto(
                        m.getId(), m.getName(),
                        m.getCharacterClass().name(),
                        m.getSlot()))
                .toList());
    }

    /** 裝備物品到主角 */
    @PostMapping("/{id}/equip")
    public ResponseEntity<EquipResult> equip(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        EquipResult result = equipmentService.equipItem(principal.getUserId(), id);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    /** 裝備物品到隊伍成員 */
    @PostMapping("/{id}/equip-member")
    public ResponseEntity<EquipResult> equipToMember(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        Long memberId = Long.parseLong(body.get("memberId").toString());
        EquipResult result = equipmentService.equipItemToMember(principal.getUserId(), id, memberId);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    /** 卸下物品（主角或隊員） */
    @PostMapping("/{id}/unequip")
    public ResponseEntity<EquipResult> unequip(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        EquipResult result = equipmentService.unequipItem(principal.getUserId(), id);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    /** 賣出物品 */
    @PostMapping("/{id}/sell")
    public ResponseEntity<EquipResult> sell(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        EquipResult result = equipmentService.sellItem(principal.getUserId(), id);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    /** 擴充背包 */
    @PostMapping("/expand")
    public ResponseEntity<EquipResult> expandInventory(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        EquipResult result = equipmentService.expandInventory(principal.getUserId());
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    // ===== DTO =====

    private static int n(Integer v) { return v != null ? v : 0; }

    private EquipmentDto toDto(UserEquipment ue) {
        var t = ue.getEquipmentTemplate();
        PartyMember member = ue.getEquippedByMember();
        return new EquipmentDto(
                ue.getId(),
                t.getName(), t.getDescription(),
                t.getEquipmentType().name(),
                t.getRarity().name(),
                t.getClassRestriction() != null ? t.getClassRestriction().name() : null,
                t.getSellPrice(),
                t.getPixelCssClass(),
                Boolean.TRUE.equals(ue.getEquippedByUser()),
                ue.getAcquiredAt().toString(),
                member != null ? member.getId() : null,
                member != null ? member.getName() : null,
                // 裝備數值（null-safe for legacy data）
                n(ue.getStatAtk()), n(ue.getStatDef()), n(ue.getStatSpd()),
                n(ue.getStatLuck()), n(ue.getStatHp()), ue.getTotalPower(),
                // 數值範圍（供前端顯示品質）
                n(t.getStatAtkMax()), n(t.getStatDefMax()), n(t.getStatSpdMax()),
                n(t.getStatLuckMax()), n(t.getStatHpMax())
        );
    }

    record EquipmentDto(
            Long id, String name, String description,
            String equipmentType, String rarity,
            String classRestriction, long sellPrice,
            String pixelCssClass, boolean equipped,
            String acquiredAt,
            Long equippedByMemberId,
            String equippedByMemberName,
            // 裝備數值
            int statAtk, int statDef, int statSpd,
            int statLuck, int statHp, int totalPower,
            // 數值上限（用於品質百分比）
            int maxAtk, int maxDef, int maxSpd,
            int maxLuck, int maxHp
    ) {}

    record PartyMemberDto(Long id, String name, String characterClass, int slot) {}
}
