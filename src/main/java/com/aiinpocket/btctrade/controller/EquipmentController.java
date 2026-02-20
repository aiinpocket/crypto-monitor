package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.entity.UserEquipment;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.EquipmentService;
import com.aiinpocket.btctrade.service.EquipmentService.EquipResult;
import com.aiinpocket.btctrade.service.EquipmentService.InventorySummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 裝備系統 REST API。
 * 提供背包查詢、穿脫、賣出、擴充功能。
 */
@RestController
@RequestMapping("/api/user/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

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

    /** 裝備物品 */
    @PostMapping("/{id}/equip")
    public ResponseEntity<EquipResult> equip(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        EquipResult result = equipmentService.equipItem(principal.getUserId(), id);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    /** 卸下物品 */
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

    private EquipmentDto toDto(UserEquipment ue) {
        var t = ue.getEquipmentTemplate();
        return new EquipmentDto(
                ue.getId(),
                t.getName(), t.getDescription(),
                t.getEquipmentType().name(),
                t.getRarity().name(),
                t.getClassRestriction() != null ? t.getClassRestriction().name() : null,
                t.getSellPrice(),
                t.getPixelCssClass(),
                Boolean.TRUE.equals(ue.getEquippedByUser()),
                ue.getAcquiredAt().toString()
        );
    }

    record EquipmentDto(
            Long id, String name, String description,
            String equipmentType, String rarity,
            String classRestriction, long sellPrice,
            String pixelCssClass, boolean equipped,
            String acquiredAt
    ) {}
}
