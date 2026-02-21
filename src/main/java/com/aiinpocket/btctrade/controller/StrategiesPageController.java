package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.model.entity.TrackedSymbol;

import java.util.List;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.TrackedSymbolService;
import com.aiinpocket.btctrade.service.UserWatchlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 策略管理和回測頁面 Controller。
 * 負責渲染策略模板管理頁和回測結果頁的 Thymeleaf 模板。
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class StrategiesPageController {

    private final UserWatchlistService watchlistService;
    private final TrackedSymbolService trackedSymbolService;

    /**
     * 渲染策略管理頁面。
     * 列出用戶可用的所有策略模板（系統預設 + 自建），
     * 並提供克隆、編輯、刪除和回測的操作入口。
     */
    @GetMapping("/strategies")
    public String strategies(
            @AuthenticationPrincipal AppUserPrincipal principal,
            Model model) {

        AppUser user = principal.getAppUser();
        log.debug("[策略頁面] 使用者 {} 載入策略管理頁面", user.getEmail());

        model.addAttribute("user", user);
        return "strategies";
    }

    /**
     * 渲染回測頁面。
     * 列出用戶的回測歷史紀錄，並顯示最新回測的結果（權益曲線 + 績效指標）。
     */
    @GetMapping("/backtest")
    public String backtest(
            @AuthenticationPrincipal AppUserPrincipal principal,
            Model model) {

        AppUser user = principal.getAppUser();
        log.debug("[回測頁面] 使用者 {} 載入回測頁面", user.getEmail());

        model.addAttribute("user", user);
        model.addAttribute("watchlistSymbols", watchlistService.getWatchlistSymbols(user.getId()));
        model.addAttribute("charClass", user.getCharacterClass() != null ? user.getCharacterClass() : "WARRIOR");
        model.addAttribute("userLevel", user.getLevel());
        // 提供所有 READY 幣對讓回測不受限於觀察清單
        List<String> readySymbols = trackedSymbolService.getReadySymbols().stream()
                .map(TrackedSymbol::getSymbol).toList();
        model.addAttribute("readySymbols", readySymbols);
        return "backtest";
    }
}
