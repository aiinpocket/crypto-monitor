package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.GameEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface GameEventLogRepository extends JpaRepository<GameEventLog, Long> {

    List<GameEventLog> findByUserIdAndSeenFalseOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE GameEventLog e SET e.seen = true WHERE e.user.id = :userId AND e.seen = false")
    int markAllSeenByUserId(Long userId);

    long countByUserIdAndEventType(Long userId, String eventType);
}
