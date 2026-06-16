package com.finanapp.controller;

import com.finanapp.model.Holding;
import com.finanapp.model.Order;
import com.finanapp.repository.HoldingRepository;
import com.finanapp.repository.OrderRepository;
import com.finanapp.service.MarketDataService;
import com.finanapp.service.RiskMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api/portfolio")
@Tag(name = "Portfolio", description = "Portfolio holdings, P&L, risk metrics, and analytics")
public class PortfolioApiController {

    private final HoldingRepository holdingRepository;
    private final OrderRepository orderRepository;
    private final MarketDataService marketDataService;
    private final RiskMetricsService riskMetricsService;

    public PortfolioApiController(HoldingRepository holdingRepository, OrderRepository orderRepository,
                                   MarketDataService marketDataService, RiskMetricsService riskMetricsService) {
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
        this.marketDataService = marketDataService;
        this.riskMetricsService = riskMetricsService;
    }

    @GetMapping("/{profile}/holdings")
    @Operation(summary = "Get all holdings for a profile", description = "Returns current holdings with live market data, P&L, and weight calculations")
    public Map<String, Object> getHoldings(@PathVariable @Parameter(description = "Profile ID e.g. JSMITH") String profile) {
        List<Holding> holdings = holdingRepository.findByProfile(profile.toUpperCase());
        List<Map<String, Object>> enriched = new ArrayList<>();
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;

        for (Holding h : holdings) {
            BigDecimal mktPrice = marketDataService.getCurrentPrice(h.getSymbol());
            BigDecimal mktValue = mktPrice.multiply(BigDecimal.valueOf(h.getQuantity()));
            BigDecimal costBasis = h.getAvgPrice().multiply(BigDecimal.valueOf(h.getQuantity()));
            BigDecimal pnl = mktValue.subtract(costBasis);
            BigDecimal pnlPct = costBasis.compareTo(BigDecimal.ZERO) > 0
                    ? pnl.divide(costBasis, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("symbol", h.getSymbol());
            row.put("quantity", h.getQuantity());
            row.put("avgPrice", h.getAvgPrice());
            row.put("marketPrice", mktPrice);
            row.put("marketValue", mktValue.setScale(2, RoundingMode.HALF_UP));
            row.put("costBasis", costBasis.setScale(2, RoundingMode.HALF_UP));
            row.put("pnl", pnl.setScale(2, RoundingMode.HALF_UP));
            row.put("pnlPercent", pnlPct.setScale(2, RoundingMode.HALF_UP));
            row.put("sector", riskMetricsService.getSector(h.getSymbol()));
            row.put("beta", riskMetricsService.getBeta(h.getSymbol()));
            enriched.add(row);

            totalMarketValue = totalMarketValue.add(mktValue);
            totalCostBasis = totalCostBasis.add(costBasis);
        }

        // Add weight
        for (Map<String, Object> row : enriched) {
            BigDecimal mv = (BigDecimal) row.get("marketValue");
            BigDecimal weight = totalMarketValue.compareTo(BigDecimal.ZERO) > 0
                    ? mv.divide(totalMarketValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            row.put("weight", weight.setScale(1, RoundingMode.HALF_UP));
        }

        BigDecimal totalPnl = totalMarketValue.subtract(totalCostBasis);
        BigDecimal totalPnlPct = totalCostBasis.compareTo(BigDecimal.ZERO) > 0
                ? totalPnl.divide(totalCostBasis, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile.toUpperCase());
        result.put("positions", enriched);
        result.put("summary", Map.of(
                "totalMarketValue", totalMarketValue.setScale(2, RoundingMode.HALF_UP),
                "totalCostBasis", totalCostBasis.setScale(2, RoundingMode.HALF_UP),
                "totalPnl", totalPnl.setScale(2, RoundingMode.HALF_UP),
                "totalPnlPercent", totalPnlPct.setScale(2, RoundingMode.HALF_UP),
                "positionCount", holdings.size()
        ));
        result.put("risk", riskMetricsService.calculateRiskMetrics(enriched));
        return result;
    }

    @GetMapping("/{profile}/risk")
    @Operation(summary = "Get portfolio risk metrics", description = "Returns beta, sector exposure, concentration risk (HHI)")
    public Map<String, Object> getRiskMetrics(@PathVariable @Parameter(description = "Profile ID") String profile) {
        var holdingsData = getHoldings(profile);
        @SuppressWarnings("unchecked")
        var positions = (List<Map<String, Object>>) holdingsData.get("positions");
        return riskMetricsService.calculateRiskMetrics(positions);
    }

    @GetMapping("/{profile}/orders")
    @Operation(summary = "Get order history for a profile (paginated)")
    public Map<String, Object> getOrders(
            @PathVariable @Parameter(description = "Profile ID") String profile,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Order> all = orderRepository.findByProfileOrderByCreatedAtDesc(profile.toUpperCase());
        int start = Math.min(page * size, all.size());
        int end = Math.min(start + size, all.size());
        List<Order> pageData = all.subList(start, end);
        return Map.of(
                "orders", pageData,
                "page", page,
                "size", size,
                "totalItems", all.size(),
                "totalPages", (int) Math.ceil((double) all.size() / size)
        );
    }
}
