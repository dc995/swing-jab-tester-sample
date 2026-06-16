package com.finanapp.repository;

import com.finanapp.model.Order;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends CrudRepository<Order, Long> {

    @Query("SELECT * FROM orders WHERE profile = :profile AND symbol = :symbol ORDER BY created_at DESC")
    List<Order> findByProfileAndSymbol(String profile, String symbol);

    @Query("SELECT * FROM orders WHERE profile = :profile ORDER BY created_at DESC")
    List<Order> findByProfileOrderByCreatedAtDesc(String profile);

    @Query("SELECT * FROM orders WHERE profile = :profile AND status = :status ORDER BY created_at DESC")
    List<Order> findByProfileAndStatus(String profile, String status);

    @Query("SELECT TOP :limit * FROM orders ORDER BY created_at DESC")
    List<Order> findRecentOrders(int limit);
}
