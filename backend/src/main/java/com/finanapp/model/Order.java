package com.finanapp.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("orders")
public class Order {

    @Id
    private Long id;

    private String profile;

    private String symbol;

    @Column("order_type")
    private String orderType;

    private int quantity;

    private BigDecimal price;

    @Column("created_at")
    private LocalDateTime createdAt;

    private String status;

    private String notes;

    public Order() {}

    public Order(String profile, String symbol, OrderType orderType, int quantity, BigDecimal price) {
        this.profile = profile;
        this.symbol = symbol;
        this.orderType = orderType.name();
        this.quantity = quantity;
        this.price = price;
        this.createdAt = LocalDateTime.now();
        this.status = "FILLED";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
