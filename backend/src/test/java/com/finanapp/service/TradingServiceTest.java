package com.finanapp.service;

import com.finanapp.model.Holding;
import com.finanapp.model.Order;
import com.finanapp.model.OrderType;
import com.finanapp.repository.HoldingRepository;
import com.finanapp.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private TradingService tradingService;

    private static final String PROFILE = "TEST";

    private void stubOrderSave() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(1L);
            return o;
        });
    }

    @Test
    void testBuyOrder_newPosition() {
        stubOrderSave();
        when(holdingRepository.findByProfileAndSymbol(PROFILE, "AAPL")).thenReturn(Optional.empty());
        when(holdingRepository.save(any(Holding.class))).thenAnswer(i -> i.getArgument(0));

        Order result = tradingService.executeBuy(PROFILE, "AAPL", 100, new BigDecimal("150.00"));

        assertNotNull(result);
        assertEquals("BUY", result.getOrderType());
        assertEquals("AAPL", result.getSymbol());
        assertEquals(PROFILE, result.getProfile());
        assertEquals(100, result.getQuantity());

        ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(captor.capture());
        Holding saved = captor.getValue();
        assertEquals("AAPL", saved.getSymbol());
        assertEquals(PROFILE, saved.getProfile());
        assertEquals(100, saved.getQuantity());
        assertEquals(0, new BigDecimal("150.00").compareTo(saved.getAvgPrice()));
    }

    @Test
    void testBuyOrder_existingPosition() {
        stubOrderSave();
        Holding existing = new Holding(PROFILE, "AAPL", 100, new BigDecimal("150.00"));
        existing.setId(1L);
        when(holdingRepository.findByProfileAndSymbol(PROFILE, "AAPL")).thenReturn(Optional.of(existing));
        when(holdingRepository.save(any(Holding.class))).thenAnswer(i -> i.getArgument(0));

        tradingService.executeBuy(PROFILE, "AAPL", 100, new BigDecimal("200.00"));

        ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(captor.capture());
        Holding saved = captor.getValue();
        assertEquals(200, saved.getQuantity());
        assertEquals(0, new BigDecimal("175.0000").compareTo(saved.getAvgPrice()));
    }

    @Test
    void testSellOrder_partialSale() {
        stubOrderSave();
        Holding existing = new Holding(PROFILE, "MSFT", 200, new BigDecimal("300.00"));
        existing.setId(1L);
        when(holdingRepository.findByProfileAndSymbol(PROFILE, "MSFT")).thenReturn(Optional.of(existing));
        when(holdingRepository.save(any(Holding.class))).thenAnswer(i -> i.getArgument(0));

        Order result = tradingService.executeSell(PROFILE, "MSFT", 50, new BigDecimal("320.00"));

        assertNotNull(result);
        assertEquals("SELL", result.getOrderType());

        ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(captor.capture());
        assertEquals(150, captor.getValue().getQuantity());
        verify(holdingRepository, never()).delete(any());
    }

    @Test
    void testSellOrder_fullLiquidation() {
        stubOrderSave();
        Holding existing = new Holding(PROFILE, "GOOG", 100, new BigDecimal("2800.00"));
        existing.setId(1L);
        when(holdingRepository.findByProfileAndSymbol(PROFILE, "GOOG")).thenReturn(Optional.of(existing));

        tradingService.executeSell(PROFILE, "GOOG", 100, new BigDecimal("2900.00"));

        verify(holdingRepository).delete(existing);
        verify(holdingRepository, never()).save(any(Holding.class));
    }

    @Test
    void testSellOrder_insufficientShares() {
        Holding existing = new Holding(PROFILE, "TSLA", 50, new BigDecimal("250.00"));
        existing.setId(1L);
        when(holdingRepository.findByProfileAndSymbol(PROFILE, "TSLA")).thenReturn(Optional.of(existing));

        InsufficientSharesException ex = assertThrows(
                InsufficientSharesException.class,
                () -> tradingService.executeSell(PROFILE, "TSLA", 100, new BigDecimal("260.00"))
        );
        assertTrue(ex.getMessage().contains("Insufficient shares"));
        assertTrue(ex.getMessage().contains("50"));
    }

    @Test
    void testSellOrder_noPosition() {
        when(holdingRepository.findByProfileAndSymbol(PROFILE, "NFLX")).thenReturn(Optional.empty());

        InsufficientSharesException ex = assertThrows(
                InsufficientSharesException.class,
                () -> tradingService.executeSell(PROFILE, "NFLX", 10, new BigDecimal("400.00"))
        );
        assertTrue(ex.getMessage().contains("No position"));
    }
}
