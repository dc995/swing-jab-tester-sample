package com.finanapp.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MarketDataService {

    // Simulated base prices (realistic as of 2026)
    private static final Map<String, Double> BASE_PRICES = Map.ofEntries(
            Map.entry("AAPL", 227.48), Map.entry("MSFT", 415.20), Map.entry("NVDA", 138.07),
            Map.entry("TSLA", 248.71), Map.entry("AMZN", 201.33), Map.entry("META", 585.25),
            Map.entry("GOOGL", 174.65), Map.entry("JNJ", 156.80), Map.entry("JPM", 242.50),
            Map.entry("PG", 168.30), Map.entry("KO", 62.45), Map.entry("BRK.B", 462.10),
            Map.entry("V", 312.70), Map.entry("XOM", 108.20), Map.entry("AMD", 165.50),
            Map.entry("NFLX", 912.30), Map.entry("DIS", 112.40), Map.entry("BA", 178.90),
            Map.entry("INTC", 24.15), Map.entry("WMT", 92.80)
    );

    private final ConcurrentHashMap<String, BigDecimal> currentPrices = new ConcurrentHashMap<>();
    private long lastTick = 0;

    public BigDecimal getCurrentPrice(String symbol) {
        refreshIfStale();
        return currentPrices.computeIfAbsent(symbol.toUpperCase(), s -> {
            double base = BASE_PRICES.getOrDefault(s, 100.0);
            return simulatePrice(base);
        });
    }

    public Map<String, BigDecimal> getAllPrices() {
        refreshIfStale();
        return Map.copyOf(currentPrices);
    }

    private void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastTick > 5000) { // refresh every 5 seconds
            lastTick = now;
            BASE_PRICES.forEach((sym, base) ->
                    currentPrices.put(sym, simulatePrice(base)));
        }
    }

    private BigDecimal simulatePrice(double base) {
        double pct = ThreadLocalRandom.current().nextDouble(-0.03, 0.03);
        double price = base * (1 + pct);
        return BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP);
    }
}
