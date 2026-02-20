package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.TradePosition;
import com.aiinpocket.btctrade.model.enums.PositionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradePositionRepository extends JpaRepository<TradePosition, Long> {

    Optional<TradePosition> findBySymbolAndStatus(String symbol, PositionStatus status);

    List<TradePosition> findByBacktestOrderByEntryTimeAsc(boolean backtest);

    List<TradePosition> findBySymbolAndBacktestOrderByEntryTimeAsc(String symbol, boolean backtest);

    // ── 用戶隔離查詢 ──

    Optional<TradePosition> findByUserIdAndSymbolAndStatus(Long userId, String symbol, PositionStatus status);

    List<TradePosition> findByUserIdAndBacktestOrderByEntryTimeAsc(Long userId, boolean backtest);

    List<TradePosition> findByUserIdAndSymbolAndBacktestOrderByEntryTimeAsc(Long userId, String symbol, boolean backtest);
}
