package com.finanapp.controller;

import com.finanapp.model.Order;
import com.finanapp.service.InsufficientSharesException;
import com.finanapp.service.TradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/trade")
@Tag(name = "Trading", description = "Execute buy and sell orders")
public class TradingApiController {

    private final TradingService tradingService;

    public TradingApiController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping("/{profile}/buy")
    @Operation(summary = "Execute a buy order", description = "Buys the specified quantity of shares at the given price. Creates or averages into existing position.")
    public ResponseEntity<Object> buy(
            @PathVariable @Parameter(description = "Profile ID e.g. JSMITH") String profile,
            @RequestParam String symbol,
            @RequestParam int quantity,
            @RequestParam BigDecimal price) {
        Order order = tradingService.executeBuy(profile.toUpperCase(), symbol.toUpperCase(), quantity, price);
        return ResponseEntity.ok(Map.of(
                "orderId", order.getId(),
                "status", "FILLED",
                "side", "BUY",
                "symbol", symbol.toUpperCase(),
                "quantity", quantity,
                "price", price
        ));
    }

    @PostMapping("/{profile}/sell")
    @Operation(summary = "Execute a sell order", description = "Sells the specified quantity of shares. Fails if insufficient holdings.")
    public ResponseEntity<Object> sell(
            @PathVariable @Parameter(description = "Profile ID") String profile,
            @RequestParam String symbol,
            @RequestParam int quantity,
            @RequestParam BigDecimal price) {
        try {
            Order order = tradingService.executeSell(profile.toUpperCase(), symbol.toUpperCase(), quantity, price);
            return ResponseEntity.ok(Map.of(
                    "orderId", order.getId(),
                    "status", "FILLED",
                    "side", "SELL",
                    "symbol", symbol.toUpperCase(),
                    "quantity", quantity,
                    "price", price
            ));
        } catch (InsufficientSharesException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "REJECTED",
                    "error", e.getMessage()
            ));
        }
    }
}
