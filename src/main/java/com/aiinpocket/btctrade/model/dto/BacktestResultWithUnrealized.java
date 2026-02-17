package com.aiinpocket.btctrade.model.dto;

import java.math.BigDecimal;

/**
 * 含未實現損益的回測結果。
 * 績效計算模式下，回測結束時不強制平倉，改為回報未平倉部位的浮盈。
 *
 * @param report              標準回測報告
 * @param unrealizedPnlPct    未平倉浮盈百分比（null 表示無持倉）
 * @param unrealizedDirection 未平倉方向 LONG/SHORT（null 表示無持倉）
 */
public record BacktestResultWithUnrealized(
        BacktestReport report,
        BigDecimal unrealizedPnlPct,
        String unrealizedDirection
) {}
