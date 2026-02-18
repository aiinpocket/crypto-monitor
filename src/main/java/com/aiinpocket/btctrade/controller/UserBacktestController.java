package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.entity.BacktestRun;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
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
            Long templateId = ((Number) body.get("templateId")).longValue();
            String symbol = ((String) body.get("symbol")).trim().toUpperCase();
            int years = body.containsKey("years") ? ((Number) body.get("years")).intValue() : 5;

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

            return ResponseEntity.ok(Map.of(
                    "id", run.getId(),
                    "status", run.getStatus().name(),
                    "message", "回測已提交，正在背景執行中"
            ));
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
            return ResponseEntity.ok(Map.of(
                    "id", run.getId(),
                    "status", run.getStatus().name(),
                    "symbol", run.getSymbol(),
                    "templateName", run.getStrategyTemplate().getName(),
                    "resultJson", run.getResultJson() != null ? run.getResultJson() : "",
                    "createdAt", run.getCreatedAt().toString(),
                    "completedAt", run.getCompletedAt() != null ? run.getCompletedAt().toString() : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
