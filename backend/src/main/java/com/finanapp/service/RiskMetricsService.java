package com.finanapp.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class RiskMetricsService {

    // Simulated sector mappings
    private static final Map<String, String> SECTOR_MAP = Map.ofEntries(
            Map.entry("AAPL", "Technology"), Map.entry("MSFT", "Technology"), Map.entry("NVDA", "Technology"),
            Map.entry("GOOGL", "Technology"), Map.entry("META", "Technology"), Map.entry("AMD", "Technology"),
            Map.entry("INTC", "Technology"), Map.entry("NFLX", "Communication Services"),
            Map.entry("TSLA", "Consumer Discretionary"), Map.entry("AMZN", "Consumer Discretionary"),
            Map.entry("DIS", "Communication Services"),
            Map.entry("JPM", "Financials"), Map.entry("V", "Financials"), Map.entry("BRK.B", "Financials"),
            Map.entry("JNJ", "Healthcare"), Map.entry("PG", "Consumer Staples"), Map.entry("KO", "Consumer Staples"),
            Map.entry("XOM", "Energy"), Map.entry("BA", "Industrials"), Map.entry("WMT", "Consumer Staples")
    );

    // Simulated beta values
    private static final Map<String, Double> BETA_MAP = Map.ofEntries(
            Map.entry("AAPL", 1.21), Map.entry("MSFT", 1.05), Map.entry("NVDA", 1.72),
            Map.entry("GOOGL", 1.15), Map.entry("META", 1.30), Map.entry("AMD", 1.65),
            Map.entry("TSLA", 1.95), Map.entry("AMZN", 1.18), Map.entry("NFLX", 1.40),
            Map.entry("JPM", 1.12), Map.entry("V", 0.98), Map.entry("BRK.B", 0.85),
            Map.entry("JNJ", 0.55), Map.entry("PG", 0.42), Map.entry("KO", 0.58),
            Map.entry("XOM", 0.90), Map.entry("BA", 1.45), Map.entry("WMT", 0.52),
            Map.entry("DIS", 1.22), Map.entry("INTC", 1.10)
    );

    public String getSector(String symbol) {
        return SECTOR_MAP.getOrDefault(symbol, "Other");
    }

    public double getBeta(String symbol) {
        return BETA_MAP.getOrDefault(symbol, 1.0);
    }

    public Map<String, Object> calculateRiskMetrics(List<Map<String, Object>> positions) {
        Map<String, BigDecimal> sectorExposure = new LinkedHashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        double weightedBeta = 0;
        BigDecimal maxPositionValue = BigDecimal.ZERO;
        String maxPositionSymbol = "";

        for (Map<String, Object> pos : positions) {
            String symbol = (String) pos.get("symbol");
            BigDecimal mv = (BigDecimal) pos.get("marketValue");
            totalValue = totalValue.add(mv);

            String sector = getSector(symbol);
            sectorExposure.merge(sector, mv, BigDecimal::add);

            if (mv.compareTo(maxPositionValue) > 0) {
                maxPositionValue = mv;
                maxPositionSymbol = symbol;
            }
        }

        // Weighted portfolio beta
        if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
            for (Map<String, Object> pos : positions) {
                String symbol = (String) pos.get("symbol");
                BigDecimal mv = (BigDecimal) pos.get("marketValue");
                double weight = mv.doubleValue() / totalValue.doubleValue();
                weightedBeta += weight * getBeta(symbol);
            }
        }

        // Sector exposure as percentages
        Map<String, BigDecimal> sectorPcts = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : sectorExposure.entrySet()) {
            if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
                sectorPcts.put(e.getKey(), e.getValue().divide(totalValue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP));
            }
        }

        // Concentration risk (HHI - Herfindahl index)
        double hhi = 0;
        for (Map<String, Object> pos : positions) {
            BigDecimal mv = (BigDecimal) pos.get("marketValue");
            if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
                double w = mv.doubleValue() / totalValue.doubleValue();
                hhi += w * w;
            }
        }

        // Concentration rating
        String concentrationRisk;
        if (hhi > 0.25) concentrationRisk = "HIGH";
        else if (hhi > 0.15) concentrationRisk = "MODERATE";
        else concentrationRisk = "LOW";

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("portfolioBeta", BigDecimal.valueOf(weightedBeta).setScale(2, RoundingMode.HALF_UP));
        metrics.put("sectorExposure", sectorPcts);
        metrics.put("herfindahlIndex", BigDecimal.valueOf(hhi).setScale(4, RoundingMode.HALF_UP));
        metrics.put("concentrationRisk", concentrationRisk);
        metrics.put("largestPosition", Map.of("symbol", maxPositionSymbol, "value", maxPositionValue.setScale(2, RoundingMode.HALF_UP)));
        metrics.put("positionCount", positions.size());
        return metrics;
    }
}
