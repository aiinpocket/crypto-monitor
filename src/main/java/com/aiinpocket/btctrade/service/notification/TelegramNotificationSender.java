package com.aiinpocket.btctrade.service.notification;

import com.aiinpocket.btctrade.model.dto.TradeNotification;
import com.aiinpocket.btctrade.model.entity.NotificationChannel;
import com.aiinpocket.btctrade.model.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Telegram 通知發送器。
 * 使用 Telegram Bot API 發送訊息到指定的 Chat ID。
 * 使用者需要自行建立 Telegram Bot 並提供 Bot Token 和 Chat ID。
 *
 * <p>configJson 必須包含：
 * <ul>
 *   <li>botToken — Telegram Bot 的認證 Token（格式：123456:ABC-DEF...）</li>
 *   <li>chatId — 目標聊天室的 ID（個人、群組或頻道）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramNotificationSender implements NotificationSender {

    private static final String TELEGRAM_API_BASE = "https://api.telegram.org";

    /** 共用的通用 RestClient Bean（由 RestClientConfig 提供） */
    private final RestClient restClient;
    /** 共用的 Jackson ObjectMapper Bean（Spring Boot 自動配置） */
    private final ObjectMapper objectMapper;

    @Override
    public ChannelType getType() {
        return ChannelType.TELEGRAM;
    }

    @Override
    public void send(NotificationChannel channel, TradeNotification notification) {
        try {
            Map<String, Object> config = parseConfig(objectMapper, channel.getConfigJson());
            String botToken = (String) config.get("botToken");
            String chatId = (String) config.get("chatId");

            // 組裝 Telegram sendMessage 請求
            Map<String, Object> body = Map.of(
                    "chat_id", chatId,
                    "text", notification.toMessageText(),
                    "parse_mode", "HTML"
            );

            restClient.post()
                    .uri(TELEGRAM_API_BASE + "/bot{token}/sendMessage", botToken)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[通知-Telegram] 訊息已發送至 chatId={}", chatId);
        } catch (Exception e) {
            log.error("[通知-Telegram] 發送失敗: channelId={}", channel.getId(), e);
        }
    }

    @Override
    public boolean testConnection(NotificationChannel channel) {
        try {
            Map<String, Object> config = parseConfig(objectMapper, channel.getConfigJson());
            String botToken = (String) config.get("botToken");
            String chatId = (String) config.get("chatId");

            Map<String, Object> body = Map.of(
                    "chat_id", chatId,
                    "text", "BtcTrade 通知測試 - 連線成功！"
            );

            restClient.post()
                    .uri(TELEGRAM_API_BASE + "/bot{token}/sendMessage", botToken)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[通知-Telegram] 連線測試成功: chatId={}", chatId);
            return true;
        } catch (Exception e) {
            log.warn("[通知-Telegram] 連線測試失敗: {}", e.getMessage());
            return false;
        }
    }
}
