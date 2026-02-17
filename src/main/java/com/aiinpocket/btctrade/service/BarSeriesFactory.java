package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.Kline;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;

import java.time.Duration;
import java.util.List;

@Component
public class BarSeriesFactory {

    public BarSeries createFromKlines(List<Kline> klines, String name) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withName(name)
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();

        for (Kline k : klines) {
            series.barBuilder()
                    .timePeriod(Duration.between(k.getOpenTime(), k.getCloseTime()))
                    .endTime(k.getCloseTime())
                    .openPrice(k.getOpenPrice())
                    .highPrice(k.getHighPrice())
                    .lowPrice(k.getLowPrice())
                    .closePrice(k.getClosePrice())
                    .volume(k.getVolume())
                    .add();
        }
        return series;
    }
}
