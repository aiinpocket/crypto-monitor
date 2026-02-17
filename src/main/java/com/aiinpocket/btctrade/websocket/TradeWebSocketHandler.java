package com.aiinpocket.btctrade.websocket;

import com.aiinpocket.btctrade.model.dto.IndicatorSnapshot;
import com.aiinpocket.btctrade.model.entity.Kline;
import com.aiinpocket.btctrade.model.enums.SyncStatus;
import com.aiinpocket.btctrade.model.enums.TradeAction;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeWebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        Long userId = getUserId(session);
        if (userId != null) {
            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
            log.info("WebSocket connected: {} (user: {})", session.getId(), userId);
        } else {
            log.info("WebSocket connected: {} (anonymous)", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        Long userId = getUserId(session);
        if (userId != null) {
            Set<WebSocketSession> set = userSessions.get(userId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
            log.info("WebSocket disconnected: {} (user: {})", session.getId(), userId);
        } else {
            log.info("WebSocket disconnected: {} (anonymous)", session.getId());
        }
    }

    public void broadcastSignal(TradeAction action, IndicatorSnapshot snapshot) {
        broadcast(Map.of(
                "type", "TRADE_SIGNAL",
                "action", action.name(),
                "price", snapshot.closePrice(),
                "rsi", snapshot.rsi(),
                "macdHistogram", snapshot.macdHistogram(),
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void broadcastPriceTick(String symbol, Kline kline) {
        broadcast(Map.of(
                "type", "PRICE_TICK",
                "symbol", symbol,
                "price", kline.getClosePrice(),
                "high", kline.getHighPrice(),
                "low", kline.getLowPrice(),
                "volume", kline.getVolume(),
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void broadcastSyncProgress(String symbol, int progress, SyncStatus status) {
        broadcast(Map.of(
                "type", "SYNC_PROGRESS",
                "symbol", symbol,
                "progress", progress,
                "status", status.name(),
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void broadcastDelistNotification(String symbol) {
        broadcast(Map.of(
                "type", "SYMBOL_DELISTED",
                "symbol", symbol,
                "message", "幣對 " + symbol + " 已從 Binance 下架",
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 對特定用戶發送 WebSocket 訊息
     */
    public void sendToUser(Long userId, Map<String, Object> payload) {
        Set<WebSocketSession> targetSessions = userSessions.get(userId);
        if (targetSessions == null || targetSessions.isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(json);
            for (var session : targetSessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.warn("Failed to send to user {} session {}", userId, session.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize user message for user {}", userId, e);
        }
    }

    private void broadcast(Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(json);
            for (var session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.warn("Failed to send message to session {}", session.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize broadcast message", e);
        }
    }

    private Long getUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        return userId instanceof Long id ? id : null;
    }
}
