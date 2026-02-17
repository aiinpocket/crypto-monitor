package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.dto.ExchangePairInfo;
import com.aiinpocket.btctrade.model.dto.SymbolRequest;
import com.aiinpocket.btctrade.model.entity.TrackedSymbol;
import com.aiinpocket.btctrade.model.enums.SyncStatus;
import com.aiinpocket.btctrade.service.BinanceExchangeInfoService;
import com.aiinpocket.btctrade.service.HistoricalSyncService;
import com.aiinpocket.btctrade.service.TrackedSymbolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/symbols")
@RequiredArgsConstructor
public class SymbolController {

    private final TrackedSymbolService trackedSymbolService;
    private final HistoricalSyncService historicalSyncService;
    private final BinanceExchangeInfoService exchangeInfoService;

    @GetMapping
    public List<TrackedSymbol> listSymbols() {
        return trackedSymbolService.getAllActiveSymbols();
    }

    @GetMapping("/{symbol}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String symbol) {
        return trackedSymbolService.getBySymbol(symbol.toUpperCase())
                .map(ts -> ResponseEntity.ok(Map.<String, Object>of(
                        "symbol", ts.getSymbol(),
                        "syncStatus", ts.getSyncStatus().name(),
                        "syncProgress", ts.getSyncProgress() != null ? ts.getSyncProgress() : 0,
                        "active", ts.isActive()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> addSymbol(@RequestBody SymbolRequest request) {
        try {
            TrackedSymbol saved = trackedSymbolService.addSymbol(
                    request.symbol(), request.displayName());

            // 自動觸發背景歷史資料同步（PENDING 或重新啟用的），從 2021/01/01 開始
            if (saved.getSyncStatus() == SyncStatus.PENDING) {
                historicalSyncService.syncHistoricalData(saved.getSymbol());
            }

            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> removeSymbol(@PathVariable String symbol) {
        trackedSymbolService.removeSymbol(symbol);
        return ResponseEntity.noContent().build();
    }

    /**
     * 查詢 Binance 可用的 USDT 交易對（快取 1 小時）。
     * 支援 search 參數篩選（不分大小寫）。
     */
    @GetMapping("/available-pairs")
    public List<ExchangePairInfo> availablePairs(
            @RequestParam(required = false, defaultValue = "") String search) {
        List<ExchangePairInfo> pairs = exchangeInfoService.getAvailablePairs();
        if (search.isBlank()) {
            return pairs;
        }
        String upper = search.toUpperCase();
        return pairs.stream()
                .filter(p -> p.symbol().contains(upper) || p.baseAsset().contains(upper))
                .toList();
    }

    /**
     * 重新啟用下架幣對。重設為 PENDING 並觸發重新同步。
     */
    @PostMapping("/{symbol}/reenable")
    public ResponseEntity<?> reenableSymbol(@PathVariable String symbol) {
        try {
            TrackedSymbol ts = trackedSymbolService.reenableSymbol(symbol.toUpperCase());
            historicalSyncService.syncHistoricalData(ts.getSymbol());
            return ResponseEntity.ok(ts);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
