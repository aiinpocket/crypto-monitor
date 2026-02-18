package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.model.entity.NotificationChannel;
import com.aiinpocket.btctrade.model.enums.ChannelType;
import com.aiinpocket.btctrade.repository.NotificationChannelRepository;
import com.aiinpocket.btctrade.service.notification.NotificationSender;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通知管道管理服務。
 * 負責使用者的通知管道 CRUD 操作，包括新增、更新、刪除管道設定，
 * 以及呼叫對應的 NotificationSender 進行連線測試。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationChannelService {

    private final NotificationChannelRepository channelRepo;
    private final List<NotificationSender> senders;

    private Map<ChannelType, NotificationSender> senderMap;

    @PostConstruct
    void init() {
        senderMap = senders.stream()
                .collect(Collectors.toMap(NotificationSender::getType, Function.identity()));
    }

    /** 取得使用者的所有通知管道 */
    public List<NotificationChannel> getChannels(Long userId) {
        return channelRepo.findByUserId(userId);
    }

    /**
     * 新增或更新通知管道。
     * 每種類型（Discord/Gmail/Telegram）每位使用者只能有一個。
     * 若已存在同類型的管道，則更新設定；否則新建。
     */
    @Transactional
    public NotificationChannel saveChannel(AppUser user, ChannelType type, String configJson,
                                            boolean enabled, boolean notifyOnEntry, boolean notifyOnExit) {
        NotificationChannel channel = channelRepo
                .findByUserIdAndChannelType(user.getId(), type)
                .orElseGet(() -> {
                    log.info("[通知管道] 使用者 {} 建立新的 {} 管道", user.getId(), type);
                    return NotificationChannel.builder()
                            .user(user)
                            .channelType(type)
                            .build();
                });

        channel.setConfigJson(configJson);
        channel.setEnabled(enabled);
        channel.setNotifyOnEntry(notifyOnEntry);
        channel.setNotifyOnExit(notifyOnExit);

        channelRepo.save(channel);
        log.info("[通知管道] 使用者 {} 的 {} 管道已儲存 (enabled={})", user.getId(), type, enabled);
        return channel;
    }

    /** 刪除通知管道（驗證所有權） */
    @Transactional
    public void deleteChannel(Long userId, Long channelId) {
        NotificationChannel channel = channelRepo.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("通知管道不存在"));
        if (!channel.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("無權操作此通知管道");
        }
        channelRepo.delete(channel);
        log.info("[通知管道] 使用者 {} 刪除 {} 管道", userId, channel.getChannelType());
    }

    /**
     * 測試通知管道的連線（驗證所有權）。
     * 會實際發送一條測試訊息，確認管道配置正確。
     */
    public boolean testChannel(Long userId, Long channelId) {
        return channelRepo.findById(channelId).map(channel -> {
            if (!channel.getUser().getId().equals(userId)) {
                log.warn("[通知管道] 使用者 {} 嘗試測試非自己的管道 {}", userId, channelId);
                return false;
            }
            NotificationSender sender = senderMap.get(channel.getChannelType());
            if (sender == null) {
                log.warn("[通知管道] 找不到 {} 類型的發送器", channel.getChannelType());
                return false;
            }
            boolean result = sender.testConnection(channel);
            log.info("[通知管道] 管道 {} ({}) 連線測試結果: {}",
                    channelId, channel.getChannelType(), result ? "成功" : "失敗");
            return result;
        }).orElse(false);
    }
}
