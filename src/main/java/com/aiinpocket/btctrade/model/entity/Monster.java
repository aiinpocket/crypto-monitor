package com.aiinpocket.btctrade.model.entity;

import com.aiinpocket.btctrade.model.enums.MonsterRiskTier;
import jakarta.persistence.*;
import lombok.*;

/**
 * 怪物定義（全域資料，約 40 隻）。
 * 每隻怪物有不同的攻擊/防禦/EXP獎勵/掉落，
 * 風險等級對應市場波動度（類似選擇權的 IV 概念）。
 */
@Entity
@Table(name = "monster", indexes = {
        @Index(name = "idx_monster_risk_tier", columnList = "risk_tier")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Monster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 怪物名稱（如「史萊姆」「火龍」等） */
    @Column(nullable = false, length = 50)
    private String name;

    /** 怪物描述 */
    @Column(length = 200)
    private String description;

    /** 風險等級，對應市場波動度區間 */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_tier", nullable = false, length = 10)
    private MonsterRiskTier riskTier;

    /** 怪物等級（1-40，影響掉落品質） */
    @Column(nullable = false)
    private Integer level;

    /** 生命值（純展示用，戰鬥結果由交易損益決定） */
    @Column(nullable = false)
    private Integer hp;

    /** 攻擊力（純展示/風味文字用） */
    @Column(nullable = false)
    private Integer atk;

    /** 防禦力（純展示/風味文字用） */
    @Column(nullable = false)
    private Integer def;

    /** 討伐成功獲得的經驗值 */
    @Column(name = "exp_reward", nullable = false)
    private Integer expReward;

    /** 最低波動率門檻（用於配對交易訊號） */
    @Column(name = "min_volatility", nullable = false)
    private Double minVolatility;

    /** 最高波動率門檻（用於配對交易訊號） */
    @Column(name = "max_volatility", nullable = false)
    private Double maxVolatility;

    /** CSS 類名，對應 head.html 中的點陣圖樣式 */
    @Column(name = "pixel_css_class", nullable = false, length = 50)
    private String pixelCssClass;

    /**
     * 是否為特殊事件怪物（僅在極端交易損益時觸發，不透過波動率匹配）。
     * true = 只在單筆交易 profitPct 超過門檻時出現。
     */
    @Column(name = "event_only", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean eventOnly = false;

    /**
     * 觸發門檻（正數=獲利怪物如 0.20 表示 +20%，負數=虧損怪物如 -0.20 表示 -20%）。
     * 僅 eventOnly=true 時有意義。null 表示非事件怪物。
     */
    @Column(name = "profit_threshold")
    private Double profitThreshold;
}
