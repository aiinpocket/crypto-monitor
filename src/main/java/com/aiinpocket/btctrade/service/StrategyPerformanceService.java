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
     * 非同步逐一計算多個模板的績效（避免多個大型回測同時執行導致 OOM）。
     * 單一 async 任務內順序執行，確保同一時間只有一個回測在跑。
     */
    @Async("backtestExecutor")
    public void computeMultiplePerformancesAsync(List<Long> templateIds) {
        log.info("[績效計算] 開始逐一計算 {} 個模板的績效", templateIds.size());
        int completed = 0;
        for (Long templateId : templateIds) {
            try {
                computePerformance(templateId);
                completed++;
            } catch (Exception e) {
                log.error("[績效計算] 模板 {} 計算失敗: {}", templateId, e.getMessage());
            }
        }
        log.info("[績效計算] 完成 {}/{} 個模板", completed, templateIds.size());
    }

    /**
     * 逐一計算所有模板的績效（供 Quartz Job 和前端「重新計算」呼叫）。
     * 改為逐一順序執行，避免多個大型回測同時佔用記憶體導致 OOM。
     */
    public void computeAllPerformances() {
        List<StrategyTemplate> allTemplates = templateRepo.findAll();
        log.info("[績效排程] 開始逐一計算 {} 個模板的績效", allTemplates.size());

        int completed = 0;
        for (StrategyTemplate template : allTemplates) {
            try {
                computePerformance(template.getId());
                completed++;
            } catch (Exception e) {
                log.error("[績效排程] 模板 {} ({}) 計算失敗: {}",
                        template.getId(), template.getName(), e.getMessage());
            }
        }
        log.info("[績效排程] 完成 {}/{} 個模板的績效計算", completed, allTemplates.size());
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
