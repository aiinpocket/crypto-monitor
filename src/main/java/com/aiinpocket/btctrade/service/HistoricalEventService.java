package com.aiinpocket.btctrade.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 加密貨幣歷史事件服務。
 * 提供重大市場事件的時間和說明，用於回測圖表標註。
 */
@Service
public class HistoricalEventService {

    private static final List<CryptoEvent> ALL_EVENTS = List.of(
            // ── BTC 減半事件 ──
            event("2012-11-28", "BTC 第一次減半", "halving", "獎勵從 50 BTC → 25 BTC"),
            event("2016-07-09", "BTC 第二次減半", "halving", "獎勵從 25 BTC → 12.5 BTC"),
            event("2020-05-11", "BTC 第三次減半", "halving", "獎勵從 12.5 BTC → 6.25 BTC"),
            event("2024-04-20", "BTC 第四次減半", "halving", "獎勵從 6.25 BTC → 3.125 BTC"),

            // ── 重大市場事件 ──
            event("2021-02-08", "Tesla 購入 15 億 BTC", "bullish", "BTC 突破 $44,000"),
            event("2021-04-14", "Coinbase 上市", "bullish", "COIN 直接上市 Nasdaq"),
            event("2021-05-19", "中國全面禁礦", "bearish", "BTC 單日跌 30%"),
            event("2021-09-07", "薩爾瓦多 BTC 法幣", "bullish", "首個將 BTC 列為法定貨幣的國家"),
            event("2021-11-10", "BTC ATH $69K", "bullish", "歷史新高 $69,044"),
            event("2022-05-09", "LUNA/UST 崩盤", "bearish", "算法穩定幣脫鉤引發連鎖清算"),
            event("2022-06-13", "Celsius 凍結提款", "bearish", "引發 CeFi 信任危機"),
            event("2022-07-05", "三箭資本破產", "bearish", "$100 億基金清算"),
            event("2022-11-08", "FTX 崩盤", "bearish", "BTC 跌至 $15,500"),
            event("2023-03-10", "SVB 銀行倒閉", "bearish", "引發避險資金流入 BTC"),
            event("2023-06-15", "BlackRock 申請 BTC ETF", "bullish", "機構入場信號"),
            event("2024-01-10", "BTC 現貨 ETF 獲批", "bullish", "SEC 批准 11 檔 BTC 現貨 ETF"),
            event("2024-03-14", "BTC 新 ATH $73K", "bullish", "ETF 資金推動新高"),
            event("2024-07-23", "ETH 現貨 ETF 獲批", "bullish", "SEC 批准 ETH 現貨 ETF"),
            event("2025-01-20", "美國加密友好政策", "bullish", "新政府就職，BTC 突破 $100K"),

            // ── 監管事件 ──
            event("2021-06-09", "薩爾瓦多通過法案", "regulation", "BTC 法定貨幣法案通過"),
            event("2023-06-05", "SEC 起訴 Binance", "regulation", "SEC vs Binance 訴訟"),
            event("2023-06-06", "SEC 起訴 Coinbase", "regulation", "SEC vs Coinbase 訴訟"),
            event("2024-10-14", "Ripple 判決", "regulation", "XRP 非證券判決")
    );

    /**
     * 取得指定日期範圍內的歷史事件。
     */
    public List<CryptoEvent> getEventsInRange(Instant start, Instant end) {
        List<CryptoEvent> result = new ArrayList<>();
        for (CryptoEvent e : ALL_EVENTS) {
            if (!e.timestamp.isBefore(start) && !e.timestamp.isAfter(end)) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * 取得所有歷史事件。
     */
    public List<CryptoEvent> getAllEvents() {
        return ALL_EVENTS;
    }

    private static CryptoEvent event(String date, String title, String category, String description) {
        Instant ts = LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new CryptoEvent(ts, title, category, description);
    }

    public record CryptoEvent(
            Instant timestamp,
            String title,
            String category,
            String description
    ) {}
}
