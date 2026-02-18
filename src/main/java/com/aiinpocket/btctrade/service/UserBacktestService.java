package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.TradingStrategyProperties;
import com.aiinpocket.btctrade.model.dto.BacktestReport;
import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.model.entity.BacktestRun;
import com.aiinpocket.btctrade.model.entity.StrategyTemplate;
import com.aiinpocket.btctrade.model.enums.BacktestRunStatus;
import com.aiinpocket.btctrade.repository.BacktestRunRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

/**
 * 用戶回測服務。
 * 管理用戶發起的回測任務生命週期：建立紀錄 → 非同步執行 → 儲存結果。
 *
 * <p>核心設計：
 * <ul>
 *   <li>回測在 {@code backtestExecutor} 獨立執行緒池中執行，不影響即時交易</li>
 *   <li>每位用戶同時只能有 1 個 RUNNING 回測，防止 CPU 過載</li>
 *   <li>結果以 JSON 序列化儲存到 DB（BacktestRun.resultJson），供前端展示</li>
 * </ul>
 */
@Service
@Slf4j
public class UserBacktestService {

    private final BacktestRunRepository runRepo;
    private final BacktestService backtestService;
    private final StrategyTemplateService templateService;
    private final ObjectMapper objectMapper;
    private final GamificationService gamificationService;
    private final TaskExecutor backtestExecutor;

    public UserBacktestService(
            BacktestRunRepository runRepo,
            BacktestService backtestService,
            StrategyTemplateService templateService,
            ObjectMapper objectMapper,
            GamificationService gamificationService,
            @Qualifier("backtestExecutor") TaskExecutor backtestExecutor) {
        this.runRepo = runRepo;
        this.backtestService = backtestService;
        this.templateService = templateService;
        this.objectMapper = objectMapper;
        this.gamificationService = gamificationService;
        this.backtestExecutor = backtestExecutor;
    }

    /** 啟動時清理因伺服器重啟而卡住的 RUNNING/PENDING 回測 */
    @PostConstruct
    void cleanupStaleRuns() {
        List<BacktestRun> staleRuns = runRepo.findByStatusIn(
                List.of(BacktestRunStatus.RUNNING, BacktestRunStatus.PENDING));
        if (!staleRuns.isEmpty()) {
            for (BacktestRun run : staleRuns) {
                run.setStatus(BacktestRunStatus.FAILED);
                run.setResultJson("{\"error\":\"伺服器重啟導致回測中斷，請重新提交\"}");
                run.setCompletedAt(Instant.now());
            }
            runRepo.saveAll(staleRuns);
            log.info("[用戶回測] 啟動清理：{} 筆卡住的回測已標記為 FAILED", staleRuns.size());
        }
    }

    /**
     * 提交回測任務。
     * 建立 BacktestRun 紀錄後立即返回，實際計算在 backtestExecutor 中非同步執行。
     *
     * @param user       發起回測的用戶
     * @param templateId 策略模板 ID
     * @param symbol     交易對符號
     * @param startDate  回測起始時間
     * @param endDate    回測結束時間
     * @return 新建立的 BacktestRun 紀錄（狀態 PENDING）
     * @throws IllegalStateException 用戶已有 RUNNING 的回測
     */
    @Transactional
    public BacktestRun submitBacktest(AppUser user, Long templateId, String symbol,
                                       Instant startDate, Instant endDate) {
        // 限制每位用戶同時只能有 1 個執行中的回測（含 PENDING + RUNNING）
        if (runRepo.existsByUserIdAndStatusIn(user.getId(),
                List.of(BacktestRunStatus.RUNNING, BacktestRunStatus.PENDING))) {
            throw new IllegalStateException("您已有一個回測正在執行中，請等待完成後再提交新的回測");
        }

        // 驗證模板存取權限
        StrategyTemplate template = templateService.getTemplate(templateId, user.getId());

        // 建立回測紀錄
        BacktestRun run = BacktestRun.builder()
                .user(user)
                .strategyTemplate(template)
                .symbol(symbol.toUpperCase())
                .startDate(startDate)
                .endDate(endDate)
                .status(BacktestRunStatus.PENDING)
                .build();
        runRepo.save(run);

        log.info("[用戶回測] 用戶 {} 提交回測: runId={}, template={}, symbol={}, period={} → {}",
                user.getId(), run.getId(), template.getName(), symbol, startDate, endDate);

        // 透過 TaskExecutor 提交非同步執行（避免 @Async 自我呼叫失效問題）
        final Long runId = run.getId();
        final TradingStrategyProperties customProps = template.toProperties();
        backtestExecutor.execute(() -> executeBacktest(runId, customProps));
        return run;
    }

    /** 在 backtestExecutor 執行緒池中執行回測（由 submitBacktest 透過 executor 提交） */
    void executeBacktest(Long runId, TradingStrategyProperties customProps) {
        BacktestRun run = runRepo.findById(runId).orElse(null);
        if (run == null) {
            log.error("[用戶回測] 找不到回測紀錄: runId={}", runId);
            return;
        }

        // 更新狀態為 RUNNING
        run.setStatus(BacktestRunStatus.RUNNING);
        runRepo.save(run);
        log.info("[用戶回測] 開始執行: runId={}, symbol={}", runId, run.getSymbol());

        try {
            // 呼叫 BacktestService 的自訂參數回測方法
            BacktestReport report = backtestService.runBacktestWithParams(
                    run.getSymbol(), run.getStartDate(), run.getEndDate(), customProps);

            // 序列化結果到 JSON
            String resultJson = objectMapper.writeValueAsString(report);
            run.setResultJson(resultJson);
            run.setStatus(BacktestRunStatus.COMPLETED);
            run.setCompletedAt(Instant.now());
            runRepo.save(run);

            log.info("[用戶回測] 完成: runId={}, trades={}, annualReturn={}%, passed={}",
                    runId, report.totalTrades(),
                    report.annualizedReturn().multiply(java.math.BigDecimal.valueOf(100)),
                    report.passed());

            // 遊戲化：回測獎勵
            try {
                if (report.passed()) {
                    gamificationService.awardExp(run.getUser(), 40, "BACKTEST_PROFIT");
                } else {
                    gamificationService.awardExp(run.getUser(), 10, "BACKTEST_COMPLETE");
                }
                gamificationService.checkAndUnlockAchievements(run.getUser(), "BACKTEST");
                gamificationService.checkBacktestMetricAchievements(
                        run.getUser(),
                        report.sharpeRatio().doubleValue(),
                        report.annualizedReturn().doubleValue());
            } catch (Exception gamEx) {
                log.warn("[遊戲化] 回測獎勵處理失敗: runId={}, error={}", runId, gamEx.getMessage());
            }
        } catch (Exception e) {
            // 回測失敗：記錄錯誤訊息
            // 使用巢狀 try-catch 確保狀態更新不會因為 DB 異常而遺失錯誤日誌
            log.error("[用戶回測] 失敗: runId={}, error={}", runId, e.getMessage(), e);
            try {
                run.setStatus(BacktestRunStatus.FAILED);
                run.setResultJson(buildErrorJson(e.getMessage()));
                run.setCompletedAt(Instant.now());
                runRepo.save(run);
            } catch (Exception dbEx) {
                log.error("[用戶回測] 儲存失敗狀態時發生 DB 錯誤: runId={}", runId, dbEx);
            }
        }
    }

    /** 查詢用戶的回測歷史（最近 10 筆，JOIN FETCH 避免 LazyInitializationException） */
    @Transactional(readOnly = true)
    public List<BacktestRun> getRecentRuns(Long userId) {
        return runRepo.findRecentByUserIdWithRelations(userId);
    }

    /** 查詢單筆回測紀錄（驗證用戶權限，JOIN FETCH 避免 LazyInitializationException） */
    @Transactional(readOnly = true)
    public BacktestRun getRun(Long runId, Long userId) {
        BacktestRun run = runRepo.findByIdWithRelations(runId)
                .orElseThrow(() -> new IllegalArgumentException("回測紀錄不存在: id=" + runId));
        if (!run.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("無權存取此回測紀錄");
        }
        return run;
    }

    /** 將錯誤訊息安全序列化為 JSON 結果（不洩漏內部細節） */
    private String buildErrorJson(String rawMessage) {
        String safeMessage = "回測計算失敗，請稍後重試";
        if (rawMessage != null && !rawMessage.contains("Exception")
                && !rawMessage.contains("SQL") && !rawMessage.contains("constraint")
                && rawMessage.length() < 200) {
            safeMessage = rawMessage;
        }
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("error", safeMessage));
        } catch (Exception e) {
            return "{\"error\":\"回測計算失敗\"}";
        }
    }
}
