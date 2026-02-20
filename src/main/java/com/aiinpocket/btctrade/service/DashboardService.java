package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.TrackedSymbol;
import com.aiinpocket.btctrade.model.entity.TradePosition;
import com.aiinpocket.btctrade.model.entity.TradeSignal;
import com.aiinpocket.btctrade.model.enums.PositionStatus;
import com.aiinpocket.btctrade.repository.KlineRepository;
import com.aiinpocket.btctrade.repository.TrackedSymbolRepository;
import com.aiinpocket.btctrade.repository.TradePositionRepository;
import com.aiinpocket.btctrade.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TradePositionRepository positionRepo;
    private final TradeSignalRepository signalRepo;
    private final KlineRepository klineRepo;
    private final TrackedSymbolRepository trackedSymbolRepo;

    public List<TrackedSymbol> getTrackedSymbols() {
        return trackedSymbolRepo.findByActiveTrue();
    }

    // ── 用戶隔離查詢（Phase 2） ──

    public List<TradePosition> getUserLivePositions(Long userId, String symbol) {
        return positionRepo.findByUserIdAndSymbolAndBacktestOrderByEntryTimeAsc(userId, symbol, false);
    }

    public Optional<TradePosition> getUserOpenPosition(Long userId, String symbol) {
        return positionRepo.findByUserIdAndSymbolAndStatus(userId, symbol, PositionStatus.OPEN);
    }

    public List<TradeSignal> getUserRecentSignals(Long userId, String symbol) {
        return signalRepo.findTop100ByUserIdAndSymbolAndBacktestOrderBySignalTimeDesc(userId, symbol, false);
    }

    // ── 全域查詢（向下相容） ──

    public List<TradePosition> getLivePositions(String symbol) {
        return positionRepo.findBySymbolAndBacktestOrderByEntryTimeAsc(symbol, false);
    }

    public Optional<TradePosition> getOpenPosition(String symbol) {
        return positionRepo.findBySymbolAndStatus(symbol, PositionStatus.OPEN);
    }

    public long getKlineCount(String symbol, String interval) {
        return klineRepo.countBySymbolAndIntervalType(symbol, interval);
    }

    public List<TradeSignal> getRecentSignals(String symbol) {
        return signalRepo.findTop100BySymbolAndBacktestOrderBySignalTimeDesc(symbol, false);
    }
}
