package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.entity.StrategyTemplate;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.GamificationService;
import com.aiinpocket.btctrade.service.StrategyTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 角色創建 Controller。
 * 首次登入（或尚未選擇職業）的用戶會被強制導向此頁面。
 * 用戶必須選擇一個職業，系統自動綁定該職業的預設策略。
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class CharacterSelectController {

    private final StrategyTemplateService templateService;
    private final GamificationService gamificationService;

    /**
     * 角色創建頁面。
     * 如果用戶已經完成角色創建，重導向回首頁。
     */
    @GetMapping("/character-select")
    public String characterSelectPage(
            @AuthenticationPrincipal AppUserPrincipal principal,
            Model model) {

        // 已完成角色創建 → 回首頁
        if (principal.getAppUser().getActiveStrategyTemplateId() != null) {
            return "redirect:/";
        }

        model.addAttribute("user", principal.getAppUser());
        return "character-select";
    }

    /**
     * 提交角色創建選擇。
     * 設定職業 + 綁定該職業的預設策略 → 重導向首頁。
     */
    @PostMapping("/api/user/character-select")
    @ResponseBody
    public ResponseEntity<?> selectCharacter(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody Map<String, String> body) {
        try {
            String characterClass = body.get("characterClass");
            if (characterClass == null || characterClass.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "請選擇一個職業"));
            }

            characterClass = characterClass.trim().toUpperCase();

            StrategyTemplate activeTemplate = templateService.selectClassAndActivateDefault(
                    principal.getUserId(), characterClass);

            // 更新 Principal 中的 AppUser（避免攔截器用舊資料重導向）
            principal.getAppUser().setCharacterClass(characterClass);
            principal.getAppUser().setActiveStrategyTemplateId(activeTemplate.getId());

            log.info("[角色創建] 用戶 {} 選擇職業: {}, 策略: '{}'",
                    principal.getUserId(), characterClass, activeTemplate.getName());

            return ResponseEntity.ok(Map.of(
                    "characterClass", characterClass,
                    "strategyName", activeTemplate.getName(),
                    "strategyId", activeTemplate.getId()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[角色創建] 失敗: userId={}", principal.getUserId(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "操作失敗，請稍後重試"));
        }
    }
}
