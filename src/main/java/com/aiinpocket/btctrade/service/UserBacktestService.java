package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.TradingStrategyProperties;
import com.aiinpocket.btctrade.model.dto.BacktestReport;
import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.model.entity.BacktestRun;
import com.aiinpocket.btctrade.model.entity.StrategyTemplate;
import com.aiinpocket.btctrade.model.enums.BacktestRunStatus;
import com.aiinpocket.btctrade.repository.BacktestRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
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
 *
 * <p>流程：
 * <ol>
 *   <li>用戶選擇策略模板、幣對和時間範圍</li>
 *   <li>{@link #submitBacktest} 建立 BacktestRun 紀錄（PENDING）並提交非同步任務</li>
 *   <li>{@link #executeBacktest} 在 backtestExecutor 中執行回測計算</li>
 *   <li>完成後更新狀態為 COMPLETED 並儲存 JSON 結果</li>
 * </ol>
 */
@Service
@Slf4j
public class UserBacktestService {

    private final BacktestRunRepository runRepo;
    private final BacktestService backtestService;
    private final StrategyTemplateService templateService;
    private final ObjectMapper objectMapper;

    /**
     * 建構子注入。ObjectMapper 由 Spring Boot 自動配置提供，
     * 確保序列化行為與全域設定一致。
     */
    public UserBacktestService(
            BacktestRunRepository runRepo,
            BacktestService backtestService,
            StrategyTemplateService templateService,
            ObjectMapper objectMapper) {
        this.runRepo = runRepo;
        this.backtestService = backtestService;
        this.templateService = templateService;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交回測任務。
     * 建立 BacktestRun 紀錄後立即返回，實際計算由 @Async 在背景執行。
     *
     * @param user       發起回測的用戶
     * @param templateId 策略模板 ID
     * @param symbol     交易對符號
     * @param startDate  回測起始時間
     * @param endDate    回測結束時間
     * @return 新建立的 BacktestRun 紀錄（狀態 PENDING）
     * @throws IllegalStateException 用戶已有 RUNNING 的回測
     */
    public BacktestRun submitBacktest(AppUser user, Long templateId, String symbol,
                                       Instant startDate, Instant endDate) {
        // 限制每位用戶同時只能有 1 個執行中的回測
        if (runRepo.existsByUserIdAndStatus(user.getId(), BacktestRunStatus.RUNNING)) {
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

        // 提交非同步執行
        executeBacktest(run.getId(), template.toProperties());
        return run;
    }

    /**
     * 非同步執行回測計算。
     * 在 backtestExecutor 執行緒池中運行，不阻塞呼叫端。
     * 計算完成後將結果序列化為 JSON 儲存到 DB。
     *
     * @param runId         回測紀錄 ID
     * @param customProps   策略模板轉換後的參數
     */
    @Async("backtestExecutor")
    public void executeBacktest(Long runId, TradingStrategyProperties customProps) {
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
        } catch (Exception e) {
            // 回測失敗：記錄錯誤訊息
            // 使用巢狀 try-catch 確保狀態更新不會因為 DB 異常而遺失錯誤日誌
            log.error("[用戶回測] 失敗: runId={}, error={}", runId, e.getMessage(), e);
            try {
                run.setStatus(BacktestRunStatus.FAILED);
                run.setResultJson("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
                run.setCompletedAt(Instant.now());
                runRepo.save(run);
            } catch (Exception dbEx) {
                log.error("[用戶回測] 儲存失敗狀態時發生 DB 錯誤: runId={}", runId, dbEx);
            }
        }
    }

    /** 查詢用戶的回測歷史（最近 10 筆） */
    public List<BacktestRun> getRecentRuns(Long userId) {
        return runRepo.findTop10ByUserIdOrderByCreatedAtDesc(userId);
    }

    /** 查詢單筆回測紀錄（驗證用戶權限） */
    public BacktestRun getRun(Long runId, Long userId) {
        BacktestRun run = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("回測紀錄不存在: id=" + runId));
        if (!run.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("無權存取此回測紀錄");
        }
        return run;
    }

    /** JSON 字串轉義（避免錯誤訊息中的特殊字元破壞 JSON 格式） */
    private String escapeJson(String input) {
        if (input == null) return "Unknown error";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
