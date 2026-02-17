package com.aiinpocket.btctrade.model.dto;

import com.aiinpocket.btctrade.model.enums.TradeAction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 交易通知 DTO。
 * 當策略產生進場或出場訊號時，封裝相關資訊用於發送到各通知管道。
 * 包含交易對、動作、價格、技術指標等關鍵資訊。
 *
 * @param symbol         交易對符號，例如 "BTCUSDT"
 * @param action         交易動作（LONG_ENTRY / SHORT_ENTRY / LONG_EXIT / SHORT_EXIT）
 * @param price          當前收盤價
 * @param rsi            RSI 指標值
 * @param macdHistogram  MACD 柱狀圖值
 * @param timestamp      訊號產生時間
 */
public record TradeNotification(
        String symbol,
        TradeAction action,
        BigDecimal price,
        BigDecimal rsi,
        BigDecimal macdHistogram,
        Instant timestamp
) {
    /**
     * 產生適合各通知管道的格式化訊息文字
     */
    public String toMessageText() {
        String actionLabel = switch (action) {
            case LONG_ENTRY -> "做多進場";
            case SHORT_ENTRY -> "做空進場";
            case LONG_EXIT -> "做多出場";
            case SHORT_EXIT -> "做空出場";
            case HOLD -> "持倉";
        };

        return String.format(
                "📊 BtcTrade 交易訊號\n\n" +
                "交易對: %s\n" +
                "動作: %s\n" +
                "價格: $%s\n" +
                "RSI: %s\n" +
                "MACD: %s\n" +
                "時間: %s",
                symbol, actionLabel, price,
                rsi != null ? rsi.toPlainString() : "N/A",
                macdHistogram != null ? macdHistogram.toPlainString() : "N/A",
                timestamp
        );
    }
}
