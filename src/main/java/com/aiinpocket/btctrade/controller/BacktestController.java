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

        log.info("Starting backtest for {} over {} years", symbol, years);

        Instant endDate = Instant.now();
        Instant startDate = endDate.minus(Duration.ofDays(365L * years));

        binanceApiService.fetchAndStoreHistoricalData(
                symbol, apiProperties.defaultInterval(), startDate, endDate);

        BacktestReport report = backtestService.runBacktest(
                symbol, startDate, endDate);

        return ResponseEntity.ok(report);
    }
}
