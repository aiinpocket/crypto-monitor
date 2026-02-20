package com.aiinpocket.btctrade.service.notification;

import com.aiinpocket.btctrade.model.dto.TradeNotification;
import com.aiinpocket.btctrade.model.entity.NotificationChannel;
import com.aiinpocket.btctrade.model.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Discord 通知發送器。
 * 使用 Discord Webhook 發送訊息到指定的頻道。
 *
 * <p>configJson 必須包含：
 * <ul>
 *   <li>webhookUrl — Discord Webhook URL（格式：https://discord.com/api/webhooks/...）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordNotificationSender implements NotificationSender {

    /** 共用的通用 RestClient Bean（由 RestClientConfig 提供） */
    private final RestClient restClient;
    /** 共用的 Jackson ObjectMapper Bean（Spring Boot 自動配置） */
    private final ObjectMapper objectMapper;

    @Override
    public ChannelType getType() {
        return ChannelType.DISCORD;
    }

    @Override
    public void send(NotificationChannel channel, TradeNotification notification) {
        try {
            Map<String, Object> config = parseConfig(objectMapper, channel.getConfigJson());
            String webhookUrl = (String) config.get("webhookUrl");

            // 組裝 Discord Embed 格式訊息（比純文字更美觀）
            String actionColor = notification.action().name().contains("ENTRY") ? "5763719" : "15548997";
            Map<String, Object> embed = Map.of(
                    "title", "BtcTrade 交易訊號",
                    "description", notification.toMessageText(),
                    "color", Integer.parseInt(actionColor)
            );
            Map<String, Object> body = Map.of(
                    "embeds", List.of(embed),
                    "username", "BtcTrade"
            );

            // 直接 POST 到 Webhook URL（不需要 Authorization header）
            restClient.post()
                    .uri(webhookUrl)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[通知-Discord] Webhook 訊息已發送");
        } catch (Exception e) {
            log.error("[通知-Discord] 發送失敗: channelId={}", channel.getId(), e);
        }
    }

    @Override
    public boolean testConnection(NotificationChannel channel) {
        try {
            Map<String, Object> config = parseConfig(objectMapper, channel.getConfigJson());
            String webhookUrl = (String) config.get("webhookUrl");

            Map<String, Object> body = Map.of(
                    "content", "BtcTrade 通知測試 - 連線成功！",
                    "username", "BtcTrade"
            );

            restClient.post()
                    .uri(webhookUrl)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[通知-Discord] Webhook 連線測試成功");
            return true;
        } catch (Exception e) {
            log.warn("[通知-Discord] Webhook 連線測試失敗: {}", e.getMessage());
            return false;
        }
    }
}
