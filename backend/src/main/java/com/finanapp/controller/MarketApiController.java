package com.finanapp.controller;

import com.finanapp.service.MarketDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
@Tag(name = "Market Data", description = "Simulated real-time market price data")
public class MarketApiController {

    private final MarketDataService marketDataService;

    public MarketApiController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/prices")
    @Operation(summary = "Get all market prices", description = "Returns current simulated prices for all tracked symbols. Refreshes every 5 seconds.")
    public Map<String, BigDecimal> getPrices() {
        return marketDataService.getAllPrices();
    }

    @GetMapping("/prices/{symbol}")
    @Operation(summary = "Get price for a single symbol")
    public Map<String, Object> getPrice(@PathVariable String symbol) {
        BigDecimal price = marketDataService.getCurrentPrice(symbol.toUpperCase());
        return Map.of("symbol", symbol.toUpperCase(), "price", price, "timestamp", System.currentTimeMillis());
    }
}
