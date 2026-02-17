package com.aiinpocket.btctrade.model.enums;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * 績效計算的時間區段。
 * 每個常數帶有中文顯示名和往前天數（null 表示固定起始日 2021-01-01）。
 */
public enum PerformancePeriod {

    SINCE_2021("2021至今", null),
    RECENT_5Y("近5年", 1825),
    RECENT_3Y("近3年", 1095),
    RECENT_1Y("近1年", 365),
    RECENT_6M("近6月", 180),
    RECENT_3M("近3月", 90),
    RECENT_1M("近1月", 30);

    private final String label;
    private final Integer daysBack;

    PerformancePeriod(String label, Integer daysBack) {
        this.label = label;
        this.daysBack = daysBack;
    }

    public String getLabel() {
        return label;
    }

    public Integer getDaysBack() {
        return daysBack;
    }

    /**
     * 計算此時段的起始時間。
     * SINCE_2021 固定為 2021-01-01T00:00:00Z，其餘往前推算 daysBack 天。
     */
    public Instant computeStartDate(Instant now) {
        if (daysBack == null) {
            return ZonedDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();
        }
        return now.minus(java.time.Duration.ofDays(daysBack));
    }
}
