package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.dto.BacktestResultWithUnrealized;
import com.aiinpocket.btctrade.model.dto.StrategyPerformanceSummary;
import com.aiinpocket.btctrade.model.dto.StrategyPerformanceSummary.PeriodMetric;
import com.aiinpocket.btctrade.model.entity.StrategyPerformance;
import com.aiinpocket.btctrade.model.entity.StrategyTemplate;
import com.aiinpocket.btctrade.model.enums.PerformancePeriod;
import com.aiinpocket.btctrade.repository.StrategyPerformanceRepository;
import com.aiinpocket.btctrade.repository.StrategyTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 策略績效計算服務。
 * 負責為每個策略模板跑 7 個時段的回測，將結果 upsert 到 DB，
 * 並提供前端查詢用的績效摘要 DTO。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyPerformanceService {

    private static final String BENCHMARK_SYMBOL = "BTCUSDT";

    private final BacktestService backtestService;
    private final StrategyTemplateRepository templateRepo;
    private final StrategyPerformanceRepository perfRepo;

    /**
     * 計算單個模板在所有時段的績效，upsert 到 DB。
     */
    @Transactional
    public void computePerformance(Long templateId) {
        StrategyTemplate template = templateRepo.findById(templateId).orElse(null);
        if (template == null) {
            log.warn("[績效計算] 模板不存在: id={}", templateId);
            return;
        }

        Instant now = Instant.now();
        var customProps = template.toProperties();
        int successCount = 0;

        for (PerformancePeriod period : PerformancePeriod.values()) {
            try {
                Instant start = period.computeStartDate(now);
                Instant end = now;

                BacktestResultWithUnrealized result = backtestService.runBacktestForPerformance(
                        BENCHMARK_SYMBOL, start, end, customProps);

                var report = result.report();

                // Upsert: 查找已有記錄或新建
                StrategyPerformance perf = perfRepo
                        .findByStrategyTemplateIdAndPeriodKeyAndSymbol(templateId, period.name(), BENCHMARK_SYMBOL)
                        .orElseGet(() -> StrategyPerformance.builder()
                                .strategyTemplate(template)
                                .symbol(BENCHMARK_SYMBOL)
                                .periodKey(period.name())
                                .periodLabel(period.getLabel())
                                .build());

                perf.setPeriodStart(start);
                perf.setPeriodEnd(end);
                perf.setWinRate(report.winRate());
                perf.setTotalReturn(report.totalReturn());
                perf.setAnnualizedReturn(report.annualizedReturn());
                perf.setMaxDrawdown(report.maxDrawdown());
                perf.setSharpeRatio(report.sharpeRatio());
                perf.setTotalTrades(report.totalTrades());
                perf.setUnrealizedPnlPct(result.unrealizedPnlPct());
                perf.setUnrealizedDirection(result.unrealizedDirection());
                perf.setComputedAt(Instant.now());

                perfRepo.save(perf);
                successCount++;
            } catch (Exception e) {
                log.warn("[績效計算] 模板 {} 時段 {} 計算失敗: {}", templateId, period.name(), e.getMessage());
            }
        }

        log.info("[績效計算] 模板 {} ({}) 完成，成功 {}/{} 個時段",
                templateId, template.getName(), successCount, PerformancePeriod.values().length);
    }

    /**
     * 非同步計算單個模板的績效（用於模板建立/修改後觸發）。
     */
    @Async("backtestExecutor")
    public void computePerformanceAsync(Long templateId) {
        computePerformance(templateId);
    }

    /**
     * 遍歷所有模板並行計算績效（供 Quartz Job 呼叫）。
     * 利用 backtestExecutor 線程池平行處理，大幅縮短全量計算時間。
     */
    public void computeAllPerformances() {
        List<StrategyTemplate> allTemplates = templateRepo.findAll();
        log.info("[績效排程] 開始非同步計算 {} 個模板的績效", allTemplates.size());

        for (StrategyTemplate template : allTemplates) {
            try {
                computePerformanceAsync(template.getId());
            } catch (Exception e) {
                log.error("[績效排程] 提交模板 {} 非同步計算失敗: {}", template.getId(), e.getMessage());
            }
        }
    }

    /**
     * 查詢用戶可見模板的績效摘要（前端 API 用）。
     */
    public List<StrategyPerformanceSummary> getPerformanceSummaries(Long userId) {
        // 1. 查詢用戶可見的模板
        List<StrategyTemplate> templates = templateRepo.findByUserIdOrSystemDefaultTrue(userId);
        if (templates.isEmpty()) return List.of();

        List<Long> templateIds = templates.stream().map(StrategyTemplate::getId).toList();

        // 2. 批次查詢績效
        List<StrategyPerformance> allPerfs = perfRepo.findByStrategyTemplateIdInAndSymbol(
                templateIds, BENCHMARK_SYMBOL);

        // 按 templateId 分組
        Map<Long, List<StrategyPerformance>> perfMap = allPerfs.stream()
                .collect(Collectors.groupingBy(p -> p.getStrategyTemplate().getId()));

        // 3. 組裝 DTO
        List<StrategyPerformanceSummary> summaries = new ArrayList<>();
        for (StrategyTemplate tmpl : templates) {
            List<StrategyPerformance> perfs = perfMap.getOrDefault(tmpl.getId(), List.of());

            List<PeriodMetric> periods = perfs.stream()
                    .map(p -> new PeriodMetric(
                            p.getPeriodKey(), p.getPeriodLabel(),
                            p.getWinRate(), p.getTotalReturn(),
                            p.getAnnualizedReturn(), p.getMaxDrawdown(),
                            p.getSharpeRatio(), p.getTotalTrades(),
                            p.getUnrealizedPnlPct(), p.getUnrealizedDirection()))
                    .toList();

            Instant lastComputed = perfs.stream()
                    .map(StrategyPerformance::getComputedAt)
                    .max(Instant::compareTo)
                    .orElse(null);

            summaries.add(new StrategyPerformanceSummary(
                    tmpl.getId(), tmpl.getName(), tmpl.getDescription(),
                    tmpl.isSystemDefault(), periods, lastComputed));
        }

        return summaries;
    }
}
