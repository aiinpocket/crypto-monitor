package com.aiinpocket.btctrade.config;

import com.aiinpocket.btctrade.job.DataFetchJob;
import com.aiinpocket.btctrade.job.PerformanceComputeJob;
import com.aiinpocket.btctrade.job.TradingEvaluationJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    // DataFetchJob 改為 GapFill 角色：每 5 分鐘執行一次，用 REST API 補漏 WebSocket 可能遺漏的資料
    @Bean
    public JobDetail dataFetchJobDetail() {
        return JobBuilder.newJob(DataFetchJob.class)
                .withIdentity("dataFetchJob", "trading")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger dataFetchTrigger(JobDetail dataFetchJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(dataFetchJobDetail)
                .withIdentity("dataFetchTrigger", "trading")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 */5 * * * ?"))
                .build();
    }

    // TradingEvaluationJob 作為備份：每小時執行一次（主要由 WebSocket 事件驅動策略評估）
    @Bean
    public JobDetail tradingEvaluationJobDetail() {
        return JobBuilder.newJob(TradingEvaluationJob.class)
                .withIdentity("tradingEvalJob", "trading")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger tradingEvaluationTrigger(JobDetail tradingEvaluationJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(tradingEvaluationJobDetail)
                .withIdentity("tradingEvalTrigger", "trading")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 * * * ?"))
                .build();
    }

    // PerformanceComputeJob：每 4 小時計算所有策略模板的績效指標
    @Bean
    public JobDetail performanceComputeJobDetail() {
        return JobBuilder.newJob(PerformanceComputeJob.class)
                .withIdentity("performanceComputeJob", "trading")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger performanceComputeTrigger(JobDetail performanceComputeJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(performanceComputeJobDetail)
                .withIdentity("performanceComputeTrigger", "trading")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 */4 * * ?"))
                .build();
    }
}
