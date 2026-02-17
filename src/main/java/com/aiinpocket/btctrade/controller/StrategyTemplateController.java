package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.entity.StrategyTemplate;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.StrategyTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 策略模板管理 REST API。
 * 提供策略模板的 CRUD 操作和克隆功能。
 *
 * <p>端點一覽：
 * <ul>
 *   <li>GET  /api/user/strategies — 取得用戶可用的所有模板</li>
 *   <li>GET  /api/user/strategies/{id} — 取得單一模板詳情</li>
 *   <li>POST /api/user/strategies/clone — 克隆模板</li>
 *   <li>PUT  /api/user/strategies/{id} — 更新自訂模板</li>
 *   <li>DELETE /api/user/strategies/{id} — 刪除自訂模板</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/user/strategies")
@RequiredArgsConstructor
@Slf4j
public class StrategyTemplateController {

    private final StrategyTemplateService templateService;

    /** 取得用戶可用的所有策略模板（系統預設 + 用戶自建） */
    @GetMapping
    public List<StrategyTemplate> getTemplates(@AuthenticationPrincipal AppUserPrincipal principal) {
        return templateService.getTemplatesForUser(principal.getUserId());
    }

    /** 取得單一策略模板的完整參數 */
    @GetMapping("/{id}")
    public StrategyTemplate getTemplate(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id) {
        return templateService.getTemplate(id, principal.getUserId());
    }

    /**
     * 克隆策略模板。
     * 請求 body 範例：
     * {
     *   "sourceId": 1,
     *   "name": "我的保守策略"
     * }
     */
    @PostMapping("/clone")
    public ResponseEntity<?> cloneTemplate(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        try {
            Long sourceId = ((Number) body.get("sourceId")).longValue();
            String name = (String) body.get("name");
            StrategyTemplate clone = templateService.cloneTemplate(
                    sourceId, principal.getAppUser(), name);

            log.info("[策略API] 用戶 {} 克隆模板: sourceId={}, newId={}",
                    principal.getUserId(), sourceId, clone.getId());
            return ResponseEntity.ok(Map.of("id", clone.getId(), "name", clone.getName()));
        } catch (Exception e) {
            log.warn("[策略API] 克隆模板失敗: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 更新用戶自訂模板的參數。
     * 系統預設模板不允許修改。
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTemplate(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            @RequestBody StrategyTemplate updates) {
        try {
            StrategyTemplate updated = templateService.updateTemplate(id, principal.getUserId(), updates);
            log.info("[策略API] 用戶 {} 更新模板 {} 成功", principal.getUserId(), id);
            return ResponseEntity.ok(Map.of("id", updated.getId(), "name", updated.getName()));
        } catch (Exception e) {
            log.warn("[策略API] 更新模板失敗: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 刪除用戶自訂模板（系統預設不可刪除） */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTemplate(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id) {
        try {
            templateService.deleteTemplate(id, principal.getUserId());
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.warn("[策略API] 刪除模板失敗: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
