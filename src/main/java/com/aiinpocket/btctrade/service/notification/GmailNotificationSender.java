package com.aiinpocket.btctrade.service.notification;

import com.aiinpocket.btctrade.model.dto.TradeNotification;
import com.aiinpocket.btctrade.model.entity.NotificationChannel;
import com.aiinpocket.btctrade.model.enums.ChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Gmail 通知發送器。
 * 使用系統預設的 SMTP 設定（application.yml 中的 spring.mail）發送郵件。
 * 使用者只需要填寫收件人 email，系統會用統一的寄件地址發送。
 *
 * <p>configJson 必須包含：
 * <ul>
 *   <li>recipientEmail — 收件人的電子郵件地址</li>
 * </ul>
 *
 * <p>注意：若系統尚未配置 SMTP（spring.mail 區塊），
 * JavaMailSender Bean 不會被建立，此發送器將無法使用。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GmailNotificationSender implements NotificationSender {

    /**
     * Spring Boot 自動注入的 JavaMailSender。
     * 若 application.yml 未配置 spring.mail，此 Bean 可能不存在。
     * 因此使用 @RequiredArgsConstructor + 允許在 send() 中檢查 null。
     */
    private final JavaMailSender mailSender;
    /** 共用的 Jackson ObjectMapper Bean（Spring Boot 自動配置） */
    private final ObjectMapper objectMapper;

    @Override
    public ChannelType getType() {
        return ChannelType.GMAIL;
    }

    @Override
    public void send(NotificationChannel channel, TradeNotification notification) {
        try {
            Map<String, Object> config = parseConfig(objectMapper, channel.getConfigJson());
            String recipientEmail = (String) config.get("recipientEmail");

            // 組裝郵件內容
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setSubject(String.format("[BtcTrade] %s %s @ $%s",
                    notification.symbol(), notification.action().name(), notification.price()));
            message.setText(notification.toMessageText());

            mailSender.send(message);
            log.info("[通知-Gmail] 郵件已發送至 {}", recipientEmail);
        } catch (Exception e) {
            log.error("[通知-Gmail] 發送失敗: channelId={}", channel.getId(), e);
        }
    }

    @Override
    public boolean testConnection(NotificationChannel channel) {
        try {
            Map<String, Object> config = parseConfig(objectMapper, channel.getConfigJson());
            String recipientEmail = (String) config.get("recipientEmail");

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setSubject("[BtcTrade] 通知測試");
            message.setText("BtcTrade 通知測試 - 連線成功！\n\n此為系統自動發送的測試郵件。");

            mailSender.send(message);
            log.info("[通知-Gmail] 連線測試成功: recipientEmail={}", recipientEmail);
            return true;
        } catch (Exception e) {
            log.warn("[通知-Gmail] 連線測試失敗: {}", e.getMessage());
            return false;
        }
    }

}
