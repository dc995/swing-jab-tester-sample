package com.finanapp.controller;

import com.finanapp.model.AuditEntry;
import com.finanapp.repository.AuditRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit Trail", description = "Activity audit log for compliance and tracking")
public class AuditApiController {

    private final AuditRepository auditRepository;

    public AuditApiController(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @GetMapping("/{profile}")
    @Operation(summary = "Get audit trail for a profile")
    public List<AuditEntry> getAuditTrail(@PathVariable String profile) {
        return auditRepository.findByProfile(profile.toUpperCase());
    }

    @GetMapping("/recent")
    @Operation(summary = "Get most recent audit entries across all profiles")
    public List<AuditEntry> getRecentAudit(@RequestParam(defaultValue = "50") int limit) {
        return auditRepository.findRecent(Math.min(limit, 200));
    }
}
