package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.BinanceApiProperties;
import com.aiinpocket.btctrade.model.enums.SyncStatus;
import com.aiinpocket.btctrade.repository.TrackedSymbolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BinanceWebSocketClient {

    private final BinanceApiProperties props;
    private final BinanceKlineMessageHandler messageHandler;
    private final TrackedSymbolRepository trackedSymbolRepo;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ConcurrentHashMap<String, WebSocket> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-reconnect");
                t.setDaemon(true);
                return t;
            });

    public void subscribe(String symbol) {
        String lowerSymbol = symbol.toLowerCase();
        String interval = props.defaultInterval();
        String streamName = lowerSymbol + "@kline_" + interval;
        String url = props.wsBaseUrl() + "/ws/" + streamName;

        log.info("Subscribing to Binance WS: {}", streamName);

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(url), new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        log.info("Binance WS connected: {}", symbol);
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            String message = buffer.toString();
                            buffer.setLength(0);
                            try {
                                messageHandler.handleMessage(message);
                            } catch (Exception e) {
                                log.error("Error handling WS message for {}", symbol, e);
                            }
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        log.warn("Binance WS closed for {}: {} {}", symbol, statusCode, reason);
                        connections.remove(symbol);
                        scheduleReconnect(symbol);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        log.error("Binance WS error for {}: {}", symbol, error.getMessage());
                        connections.remove(symbol);
                        scheduleReconnect(symbol);
                    }
                })
                .thenAccept(ws -> connections.put(symbol, ws))
                .exceptionally(e -> {
                    log.error("Failed to connect WS for {}: {}", symbol, e.getMessage());
                    scheduleReconnect(symbol);
                    return null;
                });
    }

    public void subscribeAll(List<String> symbols) {
        if (symbols.isEmpty()) return;

        if (symbols.size() <= 3) {
            symbols.forEach(this::subscribe);
            return;
        }

        // 超過 3 個符號用 combined stream
        String interval = props.defaultInterval();
        String streams = symbols.stream()
                .map(s -> s.toLowerCase() + "@kline_" + interval)
                .collect(Collectors.joining("/"));
        String url = props.wsBaseUrl() + "/stream?streams=" + streams;

        log.info("Subscribing to Binance combined WS: {} symbols", symbols.size());

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(url), new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        log.info("Binance combined WS connected ({} symbols)", symbols.size());
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            String message = buffer.toString();
                            buffer.setLength(0);
                            try {
                                messageHandler.handleMessage(message);
                            } catch (Exception e) {
                                log.error("Error handling combined WS message", e);
                            }
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        log.warn("Binance combined WS closed: {} {}", statusCode, reason);
                        connections.remove("__combined__");
                        // 重新訂閱所有符號
                        reconnectScheduler.schedule(() -> subscribeAll(symbols), 5, TimeUnit.SECONDS);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        log.error("Binance combined WS error: {}", error.getMessage());
                        connections.remove("__combined__");
                        reconnectScheduler.schedule(() -> subscribeAll(symbols), 5, TimeUnit.SECONDS);
                    }
                })
                .thenAccept(ws -> connections.put("__combined__", ws))
                .exceptionally(e -> {
                    log.error("Failed to connect combined WS: {}", e.getMessage());
                    reconnectScheduler.schedule(() -> subscribeAll(symbols), 5, TimeUnit.SECONDS);
                    return null;
                });
    }

    public void unsubscribe(String symbol) {
        WebSocket ws = connections.remove(symbol);
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "unsubscribe");
            log.info("Unsubscribed from Binance WS: {}", symbol);
        }
    }

    public void closeAll() {
        connections.forEach((key, ws) -> {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
            } catch (Exception e) {
                log.warn("Error closing WS for {}", key);
            }
        });
        connections.clear();
    }

    public boolean isConnected(String symbol) {
        WebSocket ws = connections.get(symbol);
        return ws != null && !ws.isInputClosed() && !ws.isOutputClosed();
    }

    private void scheduleReconnect(String symbol) {
        reconnectScheduler.schedule(() -> {
            // 檢查幣對是否已下架，避免無限重連產生 error log
            boolean delisted = trackedSymbolRepo.findBySymbol(symbol.toUpperCase())
                    .map(ts -> ts.getSyncStatus() == SyncStatus.DELISTED)
                    .orElse(false);

            if (delisted) {
                log.info("幣對 {} 已下架，跳過 WebSocket 重連", symbol);
                return;
            }

            log.info("Reconnecting Binance WS for {}", symbol);
            subscribe(symbol);
        }, 5, TimeUnit.SECONDS);
    }
}
