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
 * 使用 Discord Bot API 發送訊息到指定的頻道或使用者 DM。
 *
 * <p>configJson 必須包含：
 * <ul>
 *   <li>botToken — Discord Bot 的認證 Token</li>
 *   <li>channelId — 要發送到的頻道 ID（頻道廣播模式）</li>
 *   <li>targetUserId（選填）— 要發送 DM 的使用者 ID（個人通知模式）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordNotificationSender implements NotificationSender {

    private static final String DISCORD_API_BASE = "https://discord.com/api/v10";

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
            String botToken = (String) config.get("botToken");
            String channelId = (String) config.get("channelId");

            // 組裝 Discord Embed 格式訊息（比純文字更美觀）
            String actionColor = notification.action().name().contains("ENTRY") ? "5763719" : "15548997";
            Map<String, Object> embed = Map.of(
                    "title", "BtcTrade 交易訊號",
                    "description", notification.toMessageText(),
                    "color", Integer.parseInt(actionColor)
            );
            Map<String, Object> body = Map.of("embeds", List.of(embed));

            // 呼叫 Discord API 發送訊息
            restClient.post()
                    .uri(DISCORD_API_BASE + "/channels/{channelId}/messages", channelId)
                    .header("Authorization", "Bot " + botToken)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[通知-Discord] 訊息已發送至頻道 {}", channelId);
        } catch (Exception e) {
            log.error("[通知-Discord] 發送失敗: channelId={}", channel.getId(), e);
        }
    }

    @Override
    public boolean testConnection(NotificationChannel channel) {
        try {
            Map<String, Object> config = parseConfig(objectMapper, channel.getConfigJson());
            String botToken = (String) config.get("botToken");
            String channelId = (String) config.get("channelId");

            Map<String, Object> body = Map.of("content", "BtcTrade 通知測試 - 連線成功！");

            restClient.post()
                    .uri(DISCORD_API_BASE + "/channels/{channelId}/messages", channelId)
                    .header("Authorization", "Bot " + botToken)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[通知-Discord] 連線測試成功: channelId={}", channelId);
            return true;
        } catch (Exception e) {
            log.warn("[通知-Discord] 連線測試失敗: {}", e.getMessage());
            return false;
        }
    }
}
