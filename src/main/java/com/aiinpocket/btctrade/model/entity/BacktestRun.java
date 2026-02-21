package com.aiinpocket.btctrade.model.entity;

import com.aiinpocket.btctrade.model.enums.BacktestRunStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 回測執行紀錄 Entity。
 * 記錄每次用戶發起的回測任務，包含選用的策略模板、回測區間、執行狀態和結果。
 *
 * <p>回測結果以 JSON 格式儲存在 {@code resultJson} 欄位，
 * 對應 {@link com.aiinpocket.btctrade.model.dto.BacktestReport} 的序列化內容。
 * 選擇 JSON 而非正規化欄位，是因為回測報告結構複雜（含交易明細和權益曲線），
 * 且為唯讀資料，不需要 SQL 查詢其內部欄位。
 *
 * <p>生命週期：
 * <ol>
 *   <li>用戶發起回測 → 建立 PENDING 紀錄</li>
 *   <li>分配到 backtestExecutor 執行緒 → 更新為 RUNNING</li>
 *   <li>計算完成 → 更新為 COMPLETED，序列化結果到 resultJson</li>
 *   <li>計算失敗 → 更新為 FAILED，錯誤訊息寫入 resultJson</li>
 * </ol>
 */
@Entity
@Table(name = "backtest_run", indexes = {
        @Index(name = "idx_backtest_run_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 發起回測的用戶 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** 使用的策略模板 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_template_id", nullable = false)
    private StrategyTemplate strategyTemplate;

    /** 回測的交易對符號 */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** 回測起始時間 */
    @Column(nullable = false)
    private Instant startDate;

    /** 回測結束時間 */
    @Column(nullable = false)
    private Instant endDate;

    /** 執行狀態 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BacktestRunStatus status;

    /**
     * 回測結果 JSON。
     * COMPLETED 時存放 BacktestReport 的完整序列化 JSON。
     * FAILED 時存放錯誤訊息。
     */
    @Column(columnDefinition = "TEXT")
    private String resultJson;

    /** 紀錄建立時間 */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** 回測完成時間（COMPLETED 或 FAILED 時填入） */
    private Instant completedAt;

    /** 冒險事件計畫 JSON（回測提交時生成） */
    @Column(name = "adventure_json", columnDefinition = "TEXT")
    private String adventureJson;

    /** 冒險獎勵是否已領取 */
    @Column(name = "adventure_rewards_claimed")
    @Builder.Default
    private boolean adventureRewardsClaimed = false;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        if (status == null) {
            status = BacktestRunStatus.PENDING;
        }
    }
}
