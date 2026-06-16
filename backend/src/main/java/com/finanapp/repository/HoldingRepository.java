package com.finanapp.repository;

import com.finanapp.model.Holding;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingRepository extends CrudRepository<Holding, Long> {

    @Query("SELECT * FROM holdings WHERE profile = :profile AND symbol = :symbol")
    Optional<Holding> findByProfileAndSymbol(String profile, String symbol);

    @Query("SELECT * FROM holdings WHERE profile = :profile ORDER BY symbol")
    List<Holding> findByProfile(String profile);
}
