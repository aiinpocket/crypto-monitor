package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.StrategyPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 策略績效 Repository。
 * 提供批次查詢和 upsert 用的查找方法。
 */
public interface StrategyPerformanceRepository extends JpaRepository<StrategyPerformance, Long> {

    /** 批次查詢多個模板的績效（JOIN FETCH 避免 N+1） */
    @Query("SELECT p FROM StrategyPerformance p JOIN FETCH p.strategyTemplate " +
           "WHERE p.strategyTemplate.id IN :templateIds AND p.symbol = :symbol")
    List<StrategyPerformance> findByStrategyTemplateIdInAndSymbol(List<Long> templateIds, String symbol);

    /** 單筆查詢（upsert 用） */
    Optional<StrategyPerformance> findByStrategyTemplateIdAndPeriodKeyAndSymbol(
            Long templateId, String periodKey, String symbol);

    /** 模板刪除時清理績效資料 */
    void deleteByStrategyTemplateId(Long templateId);
}
