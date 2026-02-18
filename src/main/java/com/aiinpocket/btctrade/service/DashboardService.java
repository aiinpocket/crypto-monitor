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

    public List<TradePosition> getLivePositions() {
        return positionRepo.findByBacktestOrderByEntryTimeAsc(false);
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
