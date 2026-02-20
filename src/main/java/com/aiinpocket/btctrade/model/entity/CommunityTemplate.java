package com.aiinpocket.btctrade.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 社群模板 Entity。
 * 用戶將自己的自訂策略模板分享到社群，其他人可瀏覽、投票、克隆使用。
 *
 * <p>生命週期：
 * <ol>
 *   <li>用戶分享自訂模板 → 建立 ACTIVE 社群模板</li>
 *   <li>其他用戶投票（讚/噓）→ 累計 upvoteCount/downvoteCount</li>
 *   <li>評價人數 ≥ 5 且噓 > 50% → 自動標記 HIDDEN（僅提交者可見，不可重新上架）</li>
 *   <li>模板 ≥ 5 人使用且 80%+ 好評 → 提交者獲得 +1 模板額度</li>
 * </ol>
 *
 * <p>命名格式：{@code <提交者暱稱>/<策略名稱>}
 */
@Entity
@Table(name = "community_template", indexes = {
        @Index(name = "idx_community_template_submitter", columnList = "submitter_id"),
        @Index(name = "idx_community_template_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 提交者 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitter_id", nullable = false)
    @JsonIgnore
    private AppUser submitter;

    /** 來源策略模板（提交者的自訂模板） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_template_id", nullable = false)
    @JsonIgnore
    private StrategyTemplate strategyTemplate;

    /** 顯示名稱：<暱稱>/<策略名稱> */
    @Column(nullable = false, length = 200)
    private String displayName;

    /** 策略描述 */
    @Column(length = 500)
    private String description;

    /** 讚數（反正規化，避免每次 COUNT） */
    @Column(nullable = false)
    @Builder.Default
    private int upvoteCount = 0;

    /** 噓數 */
    @Column(nullable = false)
    @Builder.Default
    private int downvoteCount = 0;

    /** 使用人數（被克隆次數） */
    @Column(nullable = false)
    @Builder.Default
    private int usageCount = 0;

    /** 狀態：ACTIVE=公開, HIDDEN=被噓下架（僅提交者可見） */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /** 是否已授予提交者額外模板額度（每模板僅一次） */
    @Column(nullable = false)
    @Builder.Default
    private boolean bonusQuotaGranted = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        if (status == null) status = "ACTIVE";
    }
}
