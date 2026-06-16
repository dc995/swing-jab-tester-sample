package com.finanapp.service;

import com.finanapp.model.Holding;
import com.finanapp.model.Order;
import com.finanapp.model.OrderType;
import com.finanapp.repository.HoldingRepository;
import com.finanapp.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TradingService {

    private final OrderRepository orderRepository;
    private final HoldingRepository holdingRepository;
    private final AuditService auditService;

    public TradingService(OrderRepository orderRepository, HoldingRepository holdingRepository, AuditService auditService) {
        this.orderRepository = orderRepository;
        this.holdingRepository = holdingRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Order executeBuy(String profile, String symbol, int quantity, BigDecimal price) {
        Order order = new Order(profile, symbol, OrderType.BUY, quantity, price);
        order = orderRepository.save(order);

        Optional<Holding> existing = holdingRepository.findByProfileAndSymbol(profile, symbol);

        if (existing.isPresent()) {
            Holding holding = existing.get();
            BigDecimal oldTotal = holding.getAvgPrice()
                    .multiply(BigDecimal.valueOf(holding.getQuantity()));
            BigDecimal newTotal = price.multiply(BigDecimal.valueOf(quantity));
            int newQty = holding.getQuantity() + quantity;
            BigDecimal newAvgPrice = oldTotal.add(newTotal)
                    .divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);

            holding.setQuantity(newQty);
            holding.setAvgPrice(newAvgPrice);
            holding.setUpdatedAt(LocalDateTime.now());
            holdingRepository.save(holding);
        } else {
            Holding holding = new Holding(profile, symbol, quantity, price);
            holdingRepository.save(holding);
        }

        auditService.log(profile, "BUY", "ORDER", order.getId(),
                "BUY " + quantity + " " + symbol + " @ $" + price);
        return order;
    }

    @Transactional
    public Order executeSell(String profile, String symbol, int quantity, BigDecimal price) {
        Optional<Holding> existing = holdingRepository.findByProfileAndSymbol(profile, symbol);

        if (existing.isEmpty()) {
            throw new InsufficientSharesException("No position in " + symbol);
        }

        Holding holding = existing.get();

        if (quantity > holding.getQuantity()) {
            throw new InsufficientSharesException(
                    "Insufficient shares of " + symbol + " (have " + holding.getQuantity() + ")");
        }

        Order order = new Order(profile, symbol, OrderType.SELL, quantity, price);
        order = orderRepository.save(order);

        int newQty = holding.getQuantity() - quantity;

        if (newQty == 0) {
            holdingRepository.delete(holding);
        } else {
            holding.setQuantity(newQty);
            holding.setUpdatedAt(LocalDateTime.now());
            holdingRepository.save(holding);
        }

        auditService.log(profile, "SELL", "ORDER", order.getId(),
                "SELL " + quantity + " " + symbol + " @ $" + price);
        return order;
    }
}
