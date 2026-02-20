package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.config.BinanceApiProperties;
import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.model.entity.TrackedSymbol;
import com.aiinpocket.btctrade.model.enums.SyncStatus;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.DashboardService;
import com.aiinpocket.btctrade.service.StrategyTemplateService;
import com.aiinpocket.btctrade.service.UserWatchlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 主控台頁面 Controller。
 * 負責渲染使用者的個人化 Dashboard，包含：
 * - 使用者的觀察清單（從全域幣對池中篩選）
 * - 即時交易訊號和持倉資訊
 * - 全域幣對池（供使用者選擇加入觀察）
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;
    private final BinanceApiProperties apiProperties;
    private final UserWatchlistService watchlistService;
    private final StrategyTemplateService templateService;

    @GetMapping("/")
    public String dashboard(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String symbol,
            Model model) {

        AppUser user = principal.getAppUser();
        log.debug("[Dashboard] 使用者 {} 載入主控台", user.getEmail());

        // 取得使用者的觀察清單符號
        List<String> userSymbols = watchlistService.getWatchlistSymbols(user.getId());

        // 取得全域追蹤幣對（用於側邊欄「全域幣對池」區域）
        List<TrackedSymbol> allTrackedSymbols = dashboardService.getTrackedSymbols();

        // 從全域幣對中篩選出使用者觀察的幣對
        List<TrackedSymbol> watchedSymbols = allTrackedSymbols.stream()
                .filter(ts -> userSymbols.contains(ts.getSymbol()))
                .toList();

        // 若使用者觀察清單為空，則退回顯示全域幣對
        List<TrackedSymbol> displaySymbols = watchedSymbols.isEmpty() ? allTrackedSymbols : watchedSymbols;

        // 決定當前選中的幣對（優先順序：URL 參數 > 觀察清單第一個 > 系統預設）
        String activeSymbol = symbol != null ? symbol.toUpperCase()
                : (displaySymbols.isEmpty() ? apiProperties.defaultSymbol()
                : displaySymbols.getFirst().getSymbol());
        String interval = apiProperties.defaultInterval();

        // 將資料注入到 Thymeleaf 模型
        model.addAttribute("user", user);
        model.addAttribute("symbols", displaySymbols);
        model.addAttribute("allSymbols", allTrackedSymbols);
        model.addAttribute("userSymbols", userSymbols);
        model.addAttribute("activeSymbol", activeSymbol);
        // Phase 2: 使用用戶隔離查詢（每位用戶看自己的交易紀錄）
        Long userId = user.getId();
        model.addAttribute("livePositions", dashboardService.getUserLivePositions(userId, activeSymbol));
        model.addAttribute("openPosition", dashboardService.getUserOpenPosition(userId, activeSymbol).orElse(null));
        model.addAttribute("klineCount", dashboardService.getKlineCount(activeSymbol, interval));
        model.addAttribute("recentSignals", dashboardService.getUserRecentSignals(userId, activeSymbol));

        // 檢查當前選中的幣對是否已下架
        boolean activeSymbolDelisted = allTrackedSymbols.stream()
                .anyMatch(ts -> ts.getSymbol().equals(activeSymbol)
                        && ts.getSyncStatus() == SyncStatus.DELISTED);
        model.addAttribute("activeSymbolDelisted", activeSymbolDelisted);

        // 活躍策略資訊
        var activeStrategy = templateService.getActiveStrategy(user.getId());
        model.addAttribute("activeStrategy", activeStrategy);

        return "dashboard";
    }
}
