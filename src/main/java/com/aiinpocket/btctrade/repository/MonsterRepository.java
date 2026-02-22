package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.Monster;
import com.aiinpocket.btctrade.model.enums.MonsterRiskTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonsterRepository extends JpaRepository<Monster, Long> {

    List<Monster> findByRiskTier(MonsterRiskTier riskTier);

    List<Monster> findByMinVolatilityLessThanEqualAndMaxVolatilityGreaterThanEqual(
            Double volatility1, Double volatility2);

    /** 查詢所有特殊事件怪物 */
    List<Monster> findByEventOnlyTrue();
}
