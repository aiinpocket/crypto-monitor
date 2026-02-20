package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.dto.StrategyPerformanceSummary;
import com.aiinpocket.btctrade.model.entity.StrategyTemplate;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.StrategyPerformanceService;
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
    private final StrategyPerformanceService performanceService;

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
            if (body.get("sourceId") == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "sourceId 為必填"));
            }
            Long sourceId;
            try {
                sourceId = Long.parseLong(body.get("sourceId").toString());
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "sourceId 格式不正確"));
            }
            String name = body.get("name") != null ? body.get("name").toString().trim() : null;
            if (name != null && name.length() > 100) {
                return ResponseEntity.badRequest().body(Map.of("error", "模板名稱不能超過 100 字元"));
            }
            StrategyTemplate clone = templateService.cloneTemplate(
                    sourceId, principal.getAppUser(), name);

            log.info("[策略API] 用戶 {} 克隆模板: sourceId={}, newId={}",
                    principal.getUserId(), sourceId, clone.getId());
            return ResponseEntity.ok(Map.of("id", clone.getId(), "name", clone.getName()));
        } catch (IllegalArgumentException e) {
            log.warn("[策略API] 克隆模板失敗: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[策略API] 克隆模板意外錯誤", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "操作失敗，請稍後重試"));
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
        } catch (IllegalArgumentException e) {
            log.warn("[策略API] 更新模板失敗: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[策略API] 更新模板意外錯誤", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "操作失敗，請稍後重試"));
        }
    }

    /** 取得用戶可見策略的績效摘要 */
    @GetMapping("/performance")
    public List<StrategyPerformanceSummary> getPerformance(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return performanceService.getPerformanceSummaries(principal.getUserId());
    }

    /** 手動觸發單個模板的績效重算 */
    @PostMapping("/{id}/refresh-performance")
    public ResponseEntity<?> refreshPerformance(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id) {
        try {
            templateService.getTemplate(id, principal.getUserId()); // 驗證權限
            performanceService.computePerformanceAsync(id);
            log.info("[策略API] 用戶 {} 觸發模板 {} 績效重算", principal.getUserId(), id);
            return ResponseEntity.ok(Map.of("message", "績效計算已排入佇列"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 手動觸發所有模板的績效重算（逐一順序執行，避免 OOM） */
    @PostMapping("/refresh-all-performance")
    public ResponseEntity<?> refreshAllPerformance(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        // 防呆：檢查是否已有進行中的計算（per-user）
        var existing = performanceService.getComputeProgress(principal.getUserId());
        if (existing != null && existing.isRunning()) {
            return ResponseEntity.ok(Map.of(
                    "message", "計算已在進行中（" + existing.getCompleted() + "/" + existing.getTotal() + "），請等待完成"));
        }
        // 防呆：檢查全域計算鎖（Quartz Job 或其他用戶正在計算）
        if (performanceService.isGlobalComputeRunning()) {
            return ResponseEntity.ok(Map.of(
                    "message", "系統正在進行排程計算，請稍後再試"));
        }

        log.info("[策略API] 用戶 {} 觸發所有模板績效重算", principal.getUserId());
        var templateIds = templateService.getTemplatesForUser(principal.getUserId())
                .stream().map(t -> t.getId()).toList();
        performanceService.computeMultiplePerformancesAsync(templateIds, principal.getUserId());
        return ResponseEntity.ok(Map.of("message", "已排入 " + templateIds.size() + " 個模板的績效計算（逐一執行）"));
    }

    /** 查詢績效計算進度 */
    @GetMapping("/performance/status")
    public ResponseEntity<?> getPerformanceStatus(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        var progress = performanceService.getComputeProgress(principal.getUserId());
        if (progress == null) {
            return ResponseEntity.ok(Map.of("computing", false));
        }
        return ResponseEntity.ok(Map.of(
                "computing", progress.isRunning(),
                "completed", progress.getCompleted(),
                "total", progress.getTotal(),
                "failed", progress.getFailed()));
    }

    /** 刪除用戶自訂模板（系統預設不可刪除） */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTemplate(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id) {
        try {
            templateService.deleteTemplate(id, principal.getUserId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("[策略API] 刪除模板失敗: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[策略API] 刪除模板意外錯誤", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "操作失敗，請稍後重試"));
        }
    }
}
