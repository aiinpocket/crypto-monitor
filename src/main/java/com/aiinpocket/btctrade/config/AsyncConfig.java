package com.aiinpocket.btctrade.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 非同步任務配置。
 * 定義多個獨立的執行緒池，讓不同類型的背景任務不互相干擾：
 *
 * <ul>
 *   <li>{@code historicalSyncExecutor} — 歷史資料同步（每幣對獨立線程，避免互相阻塞）</li>
 *   <li>{@code notificationExecutor} — 通知分發專用（Discord/Gmail/Telegram 外部 API 呼叫獨立於交易邏輯）</li>
 *   <li>{@code backtestExecutor} — 用戶回測專用（CPU 密集計算不影響即時交易）</li>
 * </ul>
 *
 * <p>設計原則：每種 I/O 密集或 CPU 密集的任務使用獨立線程池，
 * 確保即使某類任務阻塞或爆量也不會拖慢其他系統功能。
 * 線程名前綴便於在 K8s 日誌中快速定位問題來源。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 歷史資料同步執行緒池。
     * 每個幣對的 Binance REST API 資料拉取在此池中獨立執行。
     * 核心 3 線程 / 最大 6 線程，確保多幣對可同時同步。
     * 隊列容量 20：當所有線程忙碌時，最多排隊 20 個同步任務。
     */
    @Bean
    public TaskExecutor historicalSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("hist-sync-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 通知分發執行緒池。
     * 負責向 Discord / Gmail / Telegram 外部 API 發送通知。
     * 與交易引擎完全隔離 — 即使通知 API 回應緩慢，也不影響策略評估和下單。
     * 核心 3 線程 / 最大 8 線程：每種通知管道可同時處理多位使用者。
     * 隊列容量 50：高頻訊號期間的通知暫存。
     */
    @Bean
    public TaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("notify-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 回測執行緒池。
     * 用戶自訂策略回測 + 績效排程計算為 CPU 密集型操作，使用獨立線程池避免影響即時交易。
     * 核心 1 線程 / 最大 2 線程：限制並行回測數量，避免多個 540K+ BarSeries 同時載入導致 OOM。
     * 隊列容量 10：績效計算改為逐一順序執行，不再大量排入。
     */
    @Bean
    public TaskExecutor backtestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("backtest-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
