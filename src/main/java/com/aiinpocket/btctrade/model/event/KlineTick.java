package com.aiinpocket.btctrade.model.event;

import com.aiinpocket.btctrade.model.entity.Kline;

public record KlineTick(String symbol, Kline kline) {}
