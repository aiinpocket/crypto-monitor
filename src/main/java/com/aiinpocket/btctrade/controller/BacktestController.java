package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.dto.BacktestReport;
import com.aiinpocket.btctrade.service.BacktestService;
import com.aiinpocket.btctrade.service.BinanceApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;

/**
 * 系統回測 API（僅供內部/管理員使用）。
 * 一般用戶應使用 /api/user/backtest/run 端點。
 *
 * <p>此端點為同步阻塞式，會先拉取歷史資料再執行回測，
 * 適合開發偵錯用途，不適合前端直接呼叫。
 */
@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final BacktestService backtestService;
    private final BinanceApiService binanceApiService;
    private final com.aiinpocket.btctrade.config.BinanceApiProperties apiProperties;

    @PostMapping("/run")
    public ResponseEntity<BacktestReport> runBacktest(
            @RequestParam(defaultValue = "BTCUSDT") String symbol,
            @RequestParam(defaultValue = "5") int years) {

        String safeSymbol = symbol.toUpperCase().trim();
        if (safeSymbol.length() > 20 || years < 1 || years > 10) {
            return ResponseEntity.badRequest().build();
        }

        log.info("[系統回測] {} over {} years", safeSymbol, years);

        Instant endDate = Instant.now();
        Instant startDate = endDate.minus(Duration.ofDays(365L * years));

        binanceApiService.fetchAndStoreHistoricalData(
                safeSymbol, apiProperties.defaultInterval(), startDate, endDate);

        BacktestReport report = backtestService.runBacktest(
                safeSymbol, startDate, endDate);

        return ResponseEntity.ok(report);
    }
}
