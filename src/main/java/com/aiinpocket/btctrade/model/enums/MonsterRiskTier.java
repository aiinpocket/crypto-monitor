package com.aiinpocket.btctrade.model.enums;

/**
 * 怪物風險等級，對應市場波動度。
 * 類似選擇權思維：同價格但不同波動歷史 = 不同風險 = 不同怪物等級。
 */
public enum MonsterRiskTier {
    LOW,
    MEDIUM,
    HIGH,
    EXTREME
}
