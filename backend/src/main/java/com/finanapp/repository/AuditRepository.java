package com.finanapp.repository;

import com.finanapp.model.AuditEntry;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditRepository extends CrudRepository<AuditEntry, Long> {

    @Query("SELECT * FROM audit_log WHERE profile = :profile ORDER BY created_at DESC")
    List<AuditEntry> findByProfile(String profile);

    @Query("SELECT TOP :limit * FROM audit_log ORDER BY created_at DESC")
    List<AuditEntry> findRecent(int limit);
}
