package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.entity.UserWatchlist;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.UserWatchlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 使用者觀察清單 REST API。
 * 提供新增、移除、查詢觀察清單的端點。
 * 所有操作都會自動綁定到當前登入的使用者。
 */
@RestController
@RequestMapping("/api/user/watchlist")
@RequiredArgsConstructor
@Slf4j
public class WatchlistController {

    private final UserWatchlistService watchlistService;

    /** 取得當前使用者的觀察清單符號列表 */
    @GetMapping
    public List<String> getWatchlist(@AuthenticationPrincipal AppUserPrincipal principal) {
        return watchlistService.getWatchlistSymbols(principal.getUserId());
    }

    /** 新增幣對到當前使用者的觀察清單 */
    @PostMapping
    public ResponseEntity<?> addSymbol(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody Map<String, String> body) {
        String symbol = body.get("symbol");
        if (symbol == null || symbol.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "symbol 為必填欄位"));
        }
        try {
            UserWatchlist entry = watchlistService.addSymbol(principal.getAppUser(), symbol);
            return ResponseEntity.ok(Map.of("symbol", entry.getSymbol(), "sortOrder", entry.getSortOrder()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 從當前使用者的觀察清單中移除幣對 */
    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> removeSymbol(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String symbol) {
        watchlistService.removeSymbol(principal.getUserId(), symbol);
        return ResponseEntity.noContent().build();
    }
}
