package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.NotificationChannel;
import com.aiinpocket.btctrade.model.enums.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 通知管道 Repository。
 * 提供按使用者、管道類型、啟用狀態等條件查詢通知管道設定的方法。
 */
public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, Long> {

    /** 查詢使用者的所有通知管道 */
    List<NotificationChannel> findByUserId(Long userId);

    /** 查詢使用者所有已啟用的通知管道（用於訊號通知發送） */
    List<NotificationChannel> findByUserIdAndEnabledTrue(Long userId);

    /** 查詢使用者特定類型的通知管道 */
    Optional<NotificationChannel> findByUserIdAndChannelType(Long userId, ChannelType channelType);

    /** 檢查使用者是否已設定特定類型的通知管道 */
    boolean existsByUserIdAndChannelType(Long userId, ChannelType channelType);
}
