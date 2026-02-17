package com.aiinpocket.btctrade.service.notification;

import com.aiinpocket.btctrade.model.dto.TradeNotification;
import com.aiinpocket.btctrade.model.entity.NotificationChannel;
import com.aiinpocket.btctrade.model.enums.ChannelType;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 通知發送器介面（Strategy Pattern）。
 * 每種通知管道（Discord / Gmail / Telegram）各有一個實作類別，
 * 由 NotificationDispatcher 根據 ChannelType 路由到對應的發送器。
 *
 * <p>提供 {@link #parseConfig} 共用預設方法，避免各實作類別重複 JSON 解析邏輯。
 */
public interface NotificationSender {

    /** 此發送器對應的管道類型 */
    ChannelType getType();

    /**
     * 發送交易通知到指定的管道。
     *
     * @param channel       通知管道設定（包含 configJson 中的連線資訊）
     * @param notification  交易通知內容
     */
    void send(NotificationChannel channel, TradeNotification notification);

    /**
     * 測試管道連線是否正常。
     * 會實際發送一條測試訊息到指定的管道。
     *
     * @param channel  通知管道設定
     * @return true 如果測試訊息發送成功
     */
    boolean testConnection(NotificationChannel channel);

    /**
     * 解析通知管道設定的 JSON 字串為 Map。
     * 所有通知發送器共用此邏輯，避免每個實作類別重複相同的解析程式碼。
     *
     * @param objectMapper 共用的 Jackson ObjectMapper Bean
     * @param configJson   管道設定 JSON 字串
     * @return 解析後的設定 Map
     * @throws IllegalArgumentException 若 JSON 格式無效
     */
    @SuppressWarnings("unchecked")
    default Map<String, Object> parseConfig(ObjectMapper objectMapper, String configJson) {
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("無效的通知管道設定 JSON", e);
        }
    }
}
