package com.aiinpocket.btctrade.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * REST 客戶端配置。
 * 定義不同用途的 RestClient Bean，避免各 Service 各自建立實例。
 *
 * <ul>
 *   <li>{@code binanceRestClient} — Binance API 專用（預設 baseUrl 和 header）</li>
 *   <li>{@code restClient} — 通用 RestClient（供通知發送器等外部 API 呼叫使用）</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(BinanceApiProperties.class)
public class RestClientConfig {

    /** Binance API 專用 RestClient，預設 baseUrl 為 Binance REST API */
    @Bean
    public RestClient binanceRestClient(BinanceApiProperties props) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /** 通用 RestClient，供通知發送器（Discord / Telegram）等外部 API 呼叫使用 */
    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }
}
