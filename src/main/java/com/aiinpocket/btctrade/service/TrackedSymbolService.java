package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.TrackedSymbol;
import com.aiinpocket.btctrade.model.enums.SyncStatus;
import com.aiinpocket.btctrade.repository.TrackedSymbolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackedSymbolService {

    private final TrackedSymbolRepository trackedSymbolRepo;

    public List<TrackedSymbol> getAllActiveSymbols() {
        return trackedSymbolRepo.findByActiveTrue();
    }

    public List<TrackedSymbol> getReadySymbols() {
        return trackedSymbolRepo.findByActiveTrueAndSyncStatus(SyncStatus.READY);
    }

    public Optional<TrackedSymbol> getBySymbol(String symbol) {
        return trackedSymbolRepo.findBySymbol(symbol);
    }

    @Transactional
    public TrackedSymbol addSymbol(String symbol, String displayName) {
        String upper = symbol.toUpperCase().trim();
        if (trackedSymbolRepo.existsBySymbol(upper)) {
            // 如果已存在但被停用，重新啟用
            TrackedSymbol existing = trackedSymbolRepo.findBySymbol(upper).orElseThrow();
            if (!existing.isActive()) {
                existing.setActive(true);
                existing.setSyncStatus(SyncStatus.PENDING);
                existing.setSyncProgress(0);
                existing.setErrorMessage(null);
                log.info("重新啟用追蹤幣對: {}", upper);
                return trackedSymbolRepo.save(existing);
            }
            throw new IllegalArgumentException("幣對 " + upper + " 已在追蹤清單中");
        }

        TrackedSymbol tracked = TrackedSymbol.builder()
                .symbol(upper)
                .displayName(displayName != null ? displayName : upper)
                .active(true)
                .syncStatus(SyncStatus.PENDING)
                .syncProgress(0)
                .build();

        TrackedSymbol saved = trackedSymbolRepo.save(tracked);
        log.info("新增追蹤幣對: {} ({})", upper, displayName);
        return saved;
    }

    @Transactional
    public void removeSymbol(String symbol) {
        String upper = symbol.toUpperCase().trim();
        trackedSymbolRepo.findBySymbol(upper).ifPresent(ts -> {
            ts.setActive(false);
            trackedSymbolRepo.save(ts);
            log.info("停用追蹤幣對: {}（資料持續更新但前端隱藏）", upper);
        });
    }

    @Transactional
    public void updateSyncStatus(String symbol, SyncStatus status, Integer progress, String errorMessage) {
        trackedSymbolRepo.findBySymbol(symbol).ifPresent(ts -> {
            ts.setSyncStatus(status);
            if (progress != null) ts.setSyncProgress(progress);
            if (errorMessage != null) ts.setErrorMessage(errorMessage);
            trackedSymbolRepo.save(ts);
        });
    }

    /** 取得可排程的幣對（active + READY） */
    public List<TrackedSymbol> getSchedulableSymbols() {
        return trackedSymbolRepo.findByActiveTrueAndSyncStatus(SyncStatus.READY);
    }

    /** 累加連續錯誤計數，返回新計數 */
    @Transactional
    public int incrementErrorCount(String symbol) {
        return trackedSymbolRepo.findBySymbol(symbol).map(ts -> {
            int newCount = ts.getConsecutiveErrorCount() + 1;
            ts.setConsecutiveErrorCount(newCount);
            trackedSymbolRepo.save(ts);
            return newCount;
        }).orElse(0);
    }

    /** 重設連續錯誤計數為 0 */
    @Transactional
    public void resetErrorCount(String symbol) {
        trackedSymbolRepo.findBySymbol(symbol).ifPresent(ts -> {
            if (ts.getConsecutiveErrorCount() > 0) {
                ts.setConsecutiveErrorCount(0);
                trackedSymbolRepo.save(ts);
            }
        });
    }

    /** 標記幣對為已下架 */
    @Transactional
    public void markAsDelisted(String symbol) {
        trackedSymbolRepo.findBySymbol(symbol).ifPresent(ts -> {
            ts.setSyncStatus(SyncStatus.DELISTED);
            ts.setDelistedAt(Instant.now());
            ts.setErrorMessage("幣對已從 Binance 下架");
            trackedSymbolRepo.save(ts);
            log.warn("幣對 {} 已標記為 DELISTED", symbol);
        });
    }

    /** 重新啟用下架幣對，重設狀態為 PENDING 以觸發重新同步 */
    @Transactional
    public TrackedSymbol reenableSymbol(String symbol) {
        TrackedSymbol ts = trackedSymbolRepo.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("找不到幣對: " + symbol));

        if (ts.getSyncStatus() != SyncStatus.DELISTED && ts.getSyncStatus() != SyncStatus.ERROR) {
            throw new IllegalArgumentException("幣對 " + symbol + " 非 DELISTED/ERROR 狀態，無需重新啟用");
        }

        ts.setSyncStatus(SyncStatus.PENDING);
        ts.setConsecutiveErrorCount(0);
        ts.setDelistedAt(null);
        ts.setErrorMessage(null);
        ts.setSyncProgress(0);
        ts.setActive(true);
        log.info("重新啟用幣對: {}", symbol);
        return trackedSymbolRepo.save(ts);
    }
}
