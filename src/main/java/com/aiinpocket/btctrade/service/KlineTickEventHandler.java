package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.event.KlineTick;
import com.aiinpocket.btctrade.websocket.TradeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KlineTickEventHandler {

    private final TradeWebSocketHandler wsHandler;

    @EventListener
    public void onKlineTick(KlineTick event) {
        wsHandler.broadcastPriceTick(event.symbol(), event.kline());
    }
}
