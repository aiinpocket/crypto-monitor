package com.aiinpocket.btctrade.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntervalConfig {

    public record IntervalParams(
            String interval,
            int barsPerDay,
            int barsPerYear,
            double sharpeAnnualizer,
            long barDurationMinutes
    ) {
        public static IntervalParams of5m() {
            int barsPerDay = 288; // 24*60/5
            int barsPerYear = barsPerDay * 365;
            return new IntervalParams("5m", barsPerDay, barsPerYear, Math.sqrt(barsPerYear), 5);
        }

        public static IntervalParams of1h() {
            int barsPerDay = 24;
            int barsPerYear = barsPerDay * 365;
            return new IntervalParams("1h", barsPerDay, barsPerYear, Math.sqrt(barsPerYear), 60);
        }

        public static IntervalParams of1d() {
            return new IntervalParams("1d", 1, 365, Math.sqrt(365), 1440);
        }

        public static IntervalParams fromString(String interval) {
            return switch (interval) {
                case "5m" -> of5m();
                case "15m" -> {
                    int bpd = 96;
                    int bpy = bpd * 365;
                    yield new IntervalParams("15m", bpd, bpy, Math.sqrt(bpy), 15);
                }
                case "1h" -> of1h();
                case "4h" -> {
                    int bpd = 6;
                    int bpy = bpd * 365;
                    yield new IntervalParams("4h", bpd, bpy, Math.sqrt(bpy), 240);
                }
                case "1d" -> of1d();
                default -> throw new IllegalArgumentException("Unsupported interval: " + interval);
            };
        }
    }

    @Bean
    public IntervalParams intervalParams(@Value("${trading.interval:5m}") String interval) {
        return IntervalParams.fromString(interval);
    }
}
