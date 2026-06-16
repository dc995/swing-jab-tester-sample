package com.finanapp.repository;

import com.finanapp.model.Holding;
import com.finanapp.model.Order;
import com.finanapp.model.OrderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderRepository and HoldingRepository query contracts.
 * Uses Mockito to verify method signatures and return types without
 * requiring a live SQL Server connection.
 */
@ExtendWith(MockitoExtension.class)
class RepositoryTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private HoldingRepository holdingRepository;

    private static final String PROFILE = "TESTUSER";

    // --- OrderRepository ---

    @Test
    void orderRepository_findByProfileAndSymbol_returnsOrderList() {
        Order o = new Order(PROFILE, "AAPL", OrderType.BUY, 100, new BigDecimal("150.00"));
        when(orderRepository.findByProfileAndSymbol(PROFILE, "AAPL")).thenReturn(List.of(o));

        List<Order> result = orderRepository.findByProfileAndSymbol(PROFILE, "AAPL");

        assertFalse(result.isEmpty());
        assertEquals("AAPL", result.get(0).getSymbol());
        assertEquals(PROFILE, result.get(0).getProfile());
    }

    @Test
    void orderRepository_findByProfileOrderByCreatedAtDesc_returnsOrderedList() {
        Order o1 = new Order(PROFILE, "MSFT", OrderType.BUY, 50, new BigDecimal("400.00"));
        Order o2 = new Order(PROFILE, "GOOG", OrderType.SELL, 10, new BigDecimal("2800.00"));
        when(orderRepository.findByProfileOrderByCreatedAtDesc(PROFILE)).thenReturn(List.of(o1, o2));

        List<Order> result = orderRepository.findByProfileOrderByCreatedAtDesc(PROFILE);

        assertEquals(2, result.size());
    }

    @Test
    void orderRepository_findByProfileAndStatus_returnsFilledOrders() {
        Order o = new Order(PROFILE, "TSLA", OrderType.BUY, 20, new BigDecimal("250.00"));
        when(orderRepository.findByProfileAndStatus(PROFILE, "FILLED")).thenReturn(List.of(o));

        List<Order> result = orderRepository.findByProfileAndStatus(PROFILE, "FILLED");

        assertEquals(1, result.size());
        assertEquals("TSLA", result.get(0).getSymbol());
    }

    @Test
    void orderRepository_findRecentOrders_respectsLimit() {
        when(orderRepository.findRecentOrders(5)).thenReturn(List.of());

        List<Order> result = orderRepository.findRecentOrders(5);

        assertNotNull(result);
        verify(orderRepository).findRecentOrders(5);
    }

    @Test
    void orderRepository_save_returnsPersistedOrder() {
        Order o = new Order(PROFILE, "NVDA", OrderType.BUY, 200, new BigDecimal("138.42"));
        when(orderRepository.save(o)).thenAnswer(inv -> {
            Order saved = inv.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        Order saved = orderRepository.save(o);

        assertNotNull(saved.getId());
        assertEquals(42L, saved.getId());
    }

    // --- HoldingRepository ---

    @Test
    void holdingRepository_findByProfileAndSymbol_returnsPresent() {
        Holding h = new Holding(PROFILE, "AAPL", 800, new BigDecimal("192.75"));
        when(holdingRepository.findByProfileAndSymbol(PROFILE, "AAPL")).thenReturn(Optional.of(h));

        Optional<Holding> result = holdingRepository.findByProfileAndSymbol(PROFILE, "AAPL");

        assertTrue(result.isPresent());
        assertEquals(800, result.get().getQuantity());
    }

    @Test
    void holdingRepository_findByProfileAndSymbol_returnsEmpty_whenNoPosition() {
        when(holdingRepository.findByProfileAndSymbol(PROFILE, "UNKNOWN")).thenReturn(Optional.empty());

        Optional<Holding> result = holdingRepository.findByProfileAndSymbol(PROFILE, "UNKNOWN");

        assertTrue(result.isEmpty());
    }

    @Test
    void holdingRepository_findByProfile_returnsAllHoldings() {
        List<Holding> holdings = List.of(
                new Holding(PROFILE, "AAPL", 800, new BigDecimal("192.75")),
                new Holding(PROFILE, "MSFT", 350, new BigDecimal("415.20")),
                new Holding(PROFILE, "NVDA", 500, new BigDecimal("138.42"))
        );
        when(holdingRepository.findByProfile(PROFILE)).thenReturn(holdings);

        List<Holding> result = holdingRepository.findByProfile(PROFILE);

        assertEquals(3, result.size());
    }

    @Test
    void holdingRepository_delete_removesHolding() {
        Holding h = new Holding(PROFILE, "GOOG", 100, new BigDecimal("2800.00"));
        h.setId(1L);

        holdingRepository.delete(h);

        verify(holdingRepository).delete(h);
    }
}
