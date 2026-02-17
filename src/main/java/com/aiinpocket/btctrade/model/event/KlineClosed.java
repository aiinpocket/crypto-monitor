package com.aiinpocket.btctrade.model.event;

import com.aiinpocket.btctrade.model.entity.Kline;

public record KlineClosed(String symbol, String interval, Kline kline) {}
