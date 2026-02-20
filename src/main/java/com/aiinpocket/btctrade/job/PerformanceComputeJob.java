package com.aiinpocket.btctrade.job;

import com.aiinpocket.btctrade.service.DistributedLockService;
import com.aiinpocket.btctrade.service.StrategyPerformanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

/**
 * 績效計算排程任務（每 4 小時）。
 * 遍歷所有策略模板，重新計算 7 個時段的回測績效。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PerformanceComputeJob extends QuartzJobBean {

    private final StrategyPerformanceService performanceService;
    private final DistributedLockService lockService;

    /** Advisory lock ID: PerformanceComputeJob 專用 */
    private static final long PERF_COMPUTE_LOCK_ID = 2_000_003L;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        lockService.executeWithLock(PERF_COMPUTE_LOCK_ID, "PerformanceComputeJob", () -> {
            log.info("[績效排程] 開始執行績效計算排程任務");
            try {
                performanceService.computeAllPerformances();
            } catch (Exception e) {
                log.error("[績效排程] 執行失敗: {}", e.getMessage(), e);
            }
        });
    }
}
