package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.Kline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface KlineRepository extends JpaRepository<Kline, Long> {

    List<Kline> findBySymbolAndIntervalTypeAndOpenTimeBetweenOrderByOpenTimeAsc(
            String symbol, String intervalType, Instant start, Instant end);

    List<Kline> findBySymbolAndIntervalTypeOrderByOpenTimeAsc(
            String symbol, String intervalType);

    Optional<Kline> findTopBySymbolAndIntervalTypeOrderByOpenTimeDesc(
            String symbol, String intervalType);

    @Query("SELECT COUNT(k) FROM Kline k WHERE k.symbol = :symbol AND k.intervalType = :intervalType")
    long countBySymbolAndIntervalType(String symbol, String intervalType);

    boolean existsBySymbolAndIntervalTypeAndOpenTime(
            String symbol, String intervalType, Instant openTime);

    /** 批次查詢已存在的 openTime（用於減少 N+1 查詢） */
    @Query("SELECT k.openTime FROM Kline k WHERE k.symbol = :symbol AND k.intervalType = :intervalType AND k.openTime IN :openTimes")
    List<Instant> findExistingOpenTimes(String symbol, String intervalType, List<Instant> openTimes);
}
