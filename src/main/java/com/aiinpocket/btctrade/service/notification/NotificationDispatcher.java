package com.aiinpocket.btctrade.service.notification;

import com.aiinpocket.btctrade.model.dto.TradeNotification;
import com.aiinpocket.btctrade.model.entity.NotificationChannel;
import com.aiinpocket.btctrade.model.enums.ChannelType;
import com.aiinpocket.btctrade.model.enums.TradeAction;
import com.aiinpocket.btctrade.repository.NotificationChannelRepository;
import com.aiinpocket.btctrade.repository.UserWatchlistRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通知分發器（Dispatcher）。
 * 當交易訊號產生時，負責：
 * 1. 查詢所有觀察了該幣對的使用者
 * 2. 對每位使用者查詢已啟用的通知管道
 * 3. 根據管道類型路由到對應的 NotificationSender 實作發送通知
 *
 * <p>整體流程為非同步（@Async("notificationExecutor")），不會阻塞策略引擎的主流程。
 * 每位使用者的通知也會提交到通知執行緒池平行發送，確保單一使用者的
 * 外部 API 呼叫延遲不會影響其他使用者的通知時效性。
 */
@Service
@Slf4j
public class NotificationDispatcher {

    private final NotificationChannelRepository channelRepo;
    private final UserWatchlistRepository watchlistRepo;
    private final List<NotificationSender> senders;
    private final TaskExecutor notificationExecutor;

    /** 管道類型 → 發送器的映射表，在啟動時初始化 */
    private Map<ChannelType, NotificationSender> senderMap;

    /**
     * 建構子注入。
     * 使用 @Qualifier 指定通知專用的執行緒池，確保通知發送與其他非同步任務隔離。
     */
    public NotificationDispatcher(
            NotificationChannelRepository channelRepo,
            UserWatchlistRepository watchlistRepo,
            List<NotificationSender> senders,
            @Qualifier("notificationExecutor") TaskExecutor notificationExecutor) {
        this.channelRepo = channelRepo;
        this.watchlistRepo = watchlistRepo;
        this.senders = senders;
        this.notificationExecutor = notificationExecutor;
    }

    @PostConstruct
    void init() {
        senderMap = senders.stream()
                .collect(Collectors.toMap(NotificationSender::getType, Function.identity()));
        log.info("[通知分發] 已註冊 {} 種通知發送器: {}",
                senderMap.size(), senderMap.keySet());
    }

    /**
     * 對所有訂閱了指定幣對的使用者發送交易通知。
     * 此方法透過 @Async 在 notificationExecutor 執行緒池中執行，不阻塞呼叫端。
     * 內部會將每位使用者的通知再次提交到執行緒池，實現多使用者平行發送。
     *
     * @param symbol       產生訊號的交易對符號
     * @param notification 交易通知內容
     */
    @Async("notificationExecutor")
    public void notifyAllSubscribers(String symbol, TradeNotification notification) {
        // 找出所有觀察清單中包含此幣對的活躍使用者 ID（排除 7 天未登入）
        Instant activeCutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        List<Long> userIds = watchlistRepo.findUserIdsBySymbol(symbol, activeCutoff);
        if (userIds.isEmpty()) {
            log.debug("[通知分發] 幣對 {} 無任何使用者訂閱，跳過通知", symbol);
            return;
        }

        log.info("[通知分發] 幣對 {} 產生 {} 訊號，通知 {} 位使用者",
                symbol, notification.action().name(), userIds.size());

        // 每位使用者的通知發送提交到執行緒池平行處理
        // 這樣單一使用者的外部 API 延遲不會拖慢其他使用者的通知
        for (Long userId : userIds) {
            notificationExecutor.execute(() -> notifyUser(userId, notification));
        }
    }

    /**
     * 對單一使用者發送交易通知。
     * 會遍歷使用者所有已啟用的通知管道，根據訂閱設定（進場/出場）決定是否發送。
     * 每個管道的發送獨立處理，單一管道失敗不影響其他管道。
     */
    /** 對單一使用者發送交易通知（Phase 2 公開給 TradeExecutionService 使用）。 */
    public void notifyUser(Long userId, TradeNotification notification) {
        List<NotificationChannel> channels = channelRepo.findByUserIdAndEnabledTrue(userId);
        if (channels.isEmpty()) {
            return;
        }

        boolean isEntry = notification.action() == TradeAction.LONG_ENTRY
                || notification.action() == TradeAction.SHORT_ENTRY;

        for (NotificationChannel channel : channels) {
            // 檢查使用者是否訂閱了此類型的通知（進場 or 出場）
            if (isEntry && !channel.isNotifyOnEntry()) {
                continue;
            }
            if (!isEntry && !channel.isNotifyOnExit()) {
                continue;
            }

            NotificationSender sender = senderMap.get(channel.getChannelType());
            if (sender == null) {
                log.warn("[通知分發] 找不到 {} 類型的發送器", channel.getChannelType());
                continue;
            }

            try {
                sender.send(channel, notification);
                log.debug("[通知分發] userId={} 通知已發送: type={}", userId, channel.getChannelType());
            } catch (Exception e) {
                log.error("[通知分發] 發送失敗: userId={}, channelId={}, type={}",
                        userId, channel.getId(), channel.getChannelType(), e);
            }
        }
    }
}
