package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.entity.BacktestRun;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.BacktestAdventureService;
import com.aiinpocket.btctrade.service.StaminaService;
import com.aiinpocket.btctrade.service.UserBacktestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 用戶回測 REST API。
 * 提供回測的提交、查詢歷史和查看結果端點。
 *
 * <p>端點一覽：
 * <ul>
 *   <li>POST /api/user/backtest/run — 提交回測任務</li>
 *   <li>GET  /api/user/backtest/history — 查詢回測歷史</li>
 *   <li>GET  /api/user/backtest/{id} — 查詢單筆回測結果</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/user/backtest")
@RequiredArgsConstructor
@Slf4j
public class UserBacktestController {

    private final UserBacktestService backtestService;
    private final BacktestAdventureService adventureService;
    private final StaminaService staminaService;

    /**
     * 提交回測任務。
     * 請求 body 範例：
     * {
     *   "templateId": 1,
     *   "symbol": "BTCUSDT",
     *   "years": 5
     * }
     *
     * <p>回測任務會立即返回，實際計算在背景的 backtestExecutor 執行緒池中非同步執行。
     * 前端可透過輪詢 GET /api/user/backtest/{id} 查看進度和結果。
     */
    @PostMapping("/run")
    public ResponseEntity<?> submitBacktest(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        try {
            if (body.get("templateId") == null || body.get("symbol") == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "templateId 和 symbol 為必填"));
            }
            Long templateId = Long.parseLong(body.get("templateId").toString());
            String symbol = body.get("symbol").toString().trim().toUpperCase();
            int years = body.containsKey("years") ? Integer.parseInt(body.get("years").toString()) : 5;

            if (symbol.isEmpty() || symbol.length() > 20) {
                return ResponseEntity.badRequest().body(Map.of("error", "symbol 格式不正確"));
            }
            if (years < 1 || years > 10) {
                return ResponseEntity.badRequest().body(Map.of("error", "回測年數需在 1~10 之間"));
            }

            // 計算回測的起始和結束時間
            Instant endDate = Instant.now();
            Instant startDate = endDate.minus(java.time.Duration.ofDays(365L * years));

            BacktestRun run = backtestService.submitBacktest(
                    principal.getAppUser(), templateId, symbol, startDate, endDate);

            log.info("[回測API] 用戶 {} 提交回測: runId={}, symbol={}, years={}",
                    principal.getUserId(), run.getId(), symbol, years);

            Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("id", run.getId());
            resp.put("status", run.getStatus().name());
            resp.put("message", "回測已提交，正在背景執行中");
            resp.put("adventureJson", run.getAdventureJson() != null ? run.getAdventureJson() : "{}");
            return ResponseEntity.ok(resp);
        } catch (IllegalStateException e) {
            // 已有 RUNNING 回測
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[回測API] 提交回測意外錯誤", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "回測提交失敗，請稍後重試"));
        }
    }

    /** 查詢用戶的回測歷史（最近 10 筆） */
    @GetMapping("/history")
    public List<Map<String, Object>> getHistory(@AuthenticationPrincipal AppUserPrincipal principal) {
        return backtestService.getRecentRuns(principal.getUserId())
                .stream()
                .map(run -> Map.<String, Object>of(
                        "id", run.getId(),
                        "symbol", run.getSymbol(),
                        "status", run.getStatus().name(),
                        "templateName", run.getStrategyTemplate().getName(),
                        "startDate", run.getStartDate().toString(),
                        "endDate", run.getEndDate().toString(),
                        "createdAt", run.getCreatedAt().toString()
                ))
                .toList();
    }

    /**
     * 查詢單筆回測結果。
     * COMPLETED 時 resultJson 包含完整的 BacktestReport JSON。
     * FAILED 時 resultJson 包含錯誤訊息。
     * RUNNING/PENDING 時 resultJson 為 null。
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getResult(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id) {
        try {
            BacktestRun run = backtestService.getRun(id, principal.getUserId());
            Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("id", run.getId());
            resp.put("status", run.getStatus().name());
            resp.put("symbol", run.getSymbol());
            resp.put("templateName", run.getStrategyTemplate().getName());
            resp.put("resultJson", run.getResultJson() != null ? run.getResultJson() : "");
            resp.put("createdAt", run.getCreatedAt().toString());
            resp.put("completedAt", run.getCompletedAt() != null ? run.getCompletedAt().toString() : "");
            resp.put("adventureJson", run.getAdventureJson() != null ? run.getAdventureJson() : "{}");
            resp.put("adventureRewardsClaimed", run.isAdventureRewardsClaimed());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 查詢體力狀態 */
    @GetMapping("/stamina")
    public ResponseEntity<?> getStamina(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.ok(staminaService.getStaminaInfo(principal.getAppUser()));
    }

    /** 使用金幣恢復體力（神父祈禱） */
    @PostMapping("/stamina/restore")
    public ResponseEntity<?> restoreStamina(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        try {
            int amount = body.containsKey("amount")
                    ? Integer.parseInt(body.get("amount").toString())
                    : principal.getAppUser().getMaxStamina(); // 預設全部恢復
            Map<String, Object> result = staminaService.restoreWithGold(principal.getAppUser(), amount);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[體力API] 恢復體力失敗", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "恢復失敗"));
        }
    }

    /** 領取冒險獎勵（回測完成後，一次性操作） */
    @PostMapping("/{id}/adventure/claim")
    public ResponseEntity<?> claimAdventureRewards(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id) {
        try {
            Map<String, Object> rewards = adventureService.claimRewards(id, principal.getUserId());
            return ResponseEntity.ok(rewards);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[冒險API] 領取獎勵失敗: runId={}", id, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "領取獎勵失敗"));
        }
    }
}
