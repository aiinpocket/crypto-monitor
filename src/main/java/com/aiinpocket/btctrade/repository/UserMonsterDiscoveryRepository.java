package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.UserMonsterDiscovery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface UserMonsterDiscoveryRepository extends JpaRepository<UserMonsterDiscovery, Long> {

    boolean existsByUserIdAndMonsterId(Long userId, Long monsterId);

    long countByUserId(Long userId);

    @Query("SELECT d.monster.id FROM UserMonsterDiscovery d WHERE d.user.id = :userId")
    Set<Long> findDiscoveredMonsterIdsByUserId(@Param("userId") Long userId);
}
