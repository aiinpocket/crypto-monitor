package com.aiinpocket.btctrade.config;

import com.aiinpocket.btctrade.websocket.TradeWebSocketHandler;
import com.aiinpocket.btctrade.websocket.UserWebSocketInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TradeWebSocketHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/trades")
                .addInterceptors(new UserWebSocketInterceptor())
                .setAllowedOrigins("*");
    }
}
