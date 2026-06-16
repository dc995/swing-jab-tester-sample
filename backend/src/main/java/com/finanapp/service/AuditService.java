package com.finanapp.service;

import com.finanapp.model.AuditEntry;
import com.finanapp.repository.AuditRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void log(String profile, String action, String entityType, Long entityId, String details) {
        auditRepository.save(new AuditEntry(profile, action, entityType, entityId, details));
    }
}
