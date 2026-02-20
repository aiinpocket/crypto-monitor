package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.TradeSignal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TradeSignalRepository extends JpaRepository<TradeSignal, Long> {

    List<TradeSignal> findBySymbolAndBacktestOrderBySignalTimeAsc(
            String symbol, boolean backtest);

    List<TradeSignal> findTop100BySymbolAndBacktestOrderBySignalTimeDesc(
            String symbol, boolean backtest);

    List<TradeSignal> findBySymbolAndSignalTimeBetweenOrderBySignalTimeAsc(
            String symbol, Instant start, Instant end);

    // ── 用戶隔離查詢 ──

    List<TradeSignal> findTop100ByUserIdAndSymbolAndBacktestOrderBySignalTimeDesc(
            Long userId, String symbol, boolean backtest);
}
