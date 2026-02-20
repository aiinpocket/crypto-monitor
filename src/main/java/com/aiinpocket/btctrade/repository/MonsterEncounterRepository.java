package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.MonsterEncounter;
import com.aiinpocket.btctrade.model.enums.BattleResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonsterEncounterRepository extends JpaRepository<MonsterEncounter, Long> {

    List<MonsterEncounter> findByUserIdOrderByStartedAtDesc(Long userId);

    List<MonsterEncounter> findByUserIdAndResult(Long userId, BattleResult result);

    long countByUserIdAndResult(Long userId, BattleResult result);

    long countByUserId(Long userId);

    /** 查找特定幣對的進行中遭遇（平倉時結算用） */
    List<MonsterEncounter> findBySymbolAndResult(String symbol, BattleResult result);
}
