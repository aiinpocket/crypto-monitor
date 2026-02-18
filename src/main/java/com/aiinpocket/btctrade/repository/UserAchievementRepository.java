package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {

    List<UserAchievement> findByUserIdOrderByUnlockedAtDesc(Long userId);

    boolean existsByUserIdAndAchievementKey(Long userId, String achievementKey);

    Optional<UserAchievement> findByUserIdAndAchievementKey(Long userId, String achievementKey);

    long countByUserIdAndAchievementKeyStartingWith(Long userId, String prefix);
}
