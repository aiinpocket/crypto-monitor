package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.MarketOverviewService;
import com.aiinpocket.btctrade.service.MarketOverviewService.MarketSummary;
import com.aiinpocket.btctrade.service.MarketOverviewService.MarketTicker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

/**
 * 市場總覽 Controller。
 * 提供頁面渲染和 REST API。
 */
@Controller
@RequiredArgsConstructor
public class MarketController {

    private final MarketOverviewService marketService;

    /** 市場總覽頁面 */
    @GetMapping("/market")
    public String marketPage(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("user", principal.getAppUser());
        return "market";
    }

    /** 市場行情 API */
    @GetMapping("/api/market/overview")
    @ResponseBody
    public ResponseEntity<List<MarketTicker>> getOverview() {
        return ResponseEntity.ok(marketService.getMarketOverview());
    }

    /** 市場摘要統計 API */
    @GetMapping("/api/market/summary")
    @ResponseBody
    public ResponseEntity<MarketSummary> getSummary() {
        return ResponseEntity.ok(marketService.getMarketSummary());
    }

    /** 強制刷新 */
    @GetMapping("/api/market/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refresh() {
        List<MarketTicker> tickers = marketService.refreshMarketData();
        return ResponseEntity.ok(Map.of("count", tickers.size(), "message", "已刷新"));
    }
}
