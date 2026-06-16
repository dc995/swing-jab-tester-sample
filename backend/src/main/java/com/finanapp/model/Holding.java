package com.finanapp.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("holdings")
public class Holding {

    @Id
    private Long id;

    private String profile;

    private String symbol;

    private int quantity;

    @Column("avg_price")
    private BigDecimal avgPrice;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Holding() {}

    public Holding(String profile, String symbol, int quantity, BigDecimal avgPrice) {
        this.profile = profile;
        this.symbol = symbol;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getAvgPrice() { return avgPrice; }
    public void setAvgPrice(BigDecimal avgPrice) { this.avgPrice = avgPrice; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
