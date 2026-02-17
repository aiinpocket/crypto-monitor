package com.aiinpocket.btctrade;

import com.aiinpocket.btctrade.config.BinanceApiProperties;
import com.aiinpocket.btctrade.config.TradingStrategyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({BinanceApiProperties.class, TradingStrategyProperties.class})
public class BtcTradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(BtcTradeApplication.class, args);
    }

}
