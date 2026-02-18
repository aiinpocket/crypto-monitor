package com.aiinpocket.btctrade.model.enums;

import lombok.Getter;

@Getter
public enum AchievementDef {

    // 登入成就
    FIRST_LOGIN("初次冒險", "完成首次登入", 10),
    LOGIN_7D("七日連續", "累計 7 天登入", 50),
    LOGIN_30D("月光旅人", "累計 30 天登入", 200),
    LOGIN_100D("百日征途", "累計 100 天登入", 500),

    // 回測成就
    FIRST_BACKTEST("初次回測", "完成首次策略回測", 30),
    PROFITABLE_1("初嚐勝果", "首次獲利回測", 50),
    PROFITABLE_10("連戰連勝", "累計 10 次獲利回測", 200),

    // 策略成就
    STRATEGY_CLONE("策略複製師", "首次克隆策略模板", 20),
    STRATEGY_5("策略大師", "擁有 5 個策略模板", 100),

    // 觀察清單成就
    WATCHLIST_5("觀察者", "觀察清單達 5 個幣對", 30),

    // 等級成就
    LEVEL_5("銅級交易員", "達到 Lv.5", 0),
    LEVEL_10("銀級交易員", "達到 Lv.10", 0),
    LEVEL_25("金級交易員", "達到 Lv.25", 0),
    LEVEL_50("傳說交易員", "達到 Lv.50", 0),

    // 績效成就
    SHARPE_1("風險大師", "回測 Sharpe Ratio > 1.0", 100),
    ANNUAL_30("年化 30%+", "回測年化報酬 > 30%", 150);

    private final String displayName;
    private final String description;
    private final int expReward;

    AchievementDef(String displayName, String description, int expReward) {
        this.displayName = displayName;
        this.description = description;
        this.expReward = expReward;
    }
}
