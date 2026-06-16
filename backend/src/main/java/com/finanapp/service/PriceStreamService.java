package com.finanapp.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@EnableScheduling
public class PriceStreamService {

    private final MarketDataService marketDataService;
    private final SimpMessagingTemplate messagingTemplate;

    public PriceStreamService(MarketDataService marketDataService, SimpMessagingTemplate messagingTemplate) {
        this.marketDataService = marketDataService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRate = 3000)
    public void broadcastPrices() {
        Map<String, BigDecimal> prices = marketDataService.getAllPrices();
        messagingTemplate.convertAndSend("/topic/prices", prices);
    }
}
