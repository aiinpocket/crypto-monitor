package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.UserMonsterDiscovery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

public interface UserMonsterDiscoveryRepository extends JpaRepository<UserMonsterDiscovery, Long> {

    boolean existsByUserIdAndMonsterId(Long userId, Long monsterId);

    long countByUserId(Long userId);

    @Query("SELECT d.monster.id FROM UserMonsterDiscovery d WHERE d.user.id = :userId")
    Set<Long> findDiscoveredMonsterIdsByUserId(@Param("userId") Long userId);

    /**
     * 原子性記錄怪物發現（冪等）。
     * 使用 INSERT ON CONFLICT DO NOTHING 避免 Check-Then-Act 競態條件。
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO user_monster_discovery (user_id, monster_id, discovered_at) " +
            "VALUES (:userId, :monsterId, NOW()) " +
            "ON CONFLICT ON CONSTRAINT uk_user_monster_discovery DO NOTHING",
            nativeQuery = true)
    void discoverOrIgnore(@Param("userId") Long userId, @Param("monsterId") Long monsterId);
}
