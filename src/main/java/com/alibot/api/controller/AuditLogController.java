package com.alibot.api.controller;

import com.alibot.api.dto.AuditLogResponse;
import com.alibot.api.security.CurrentActor;
import com.alibot.service.AuditLogService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ТЗ п.95 — журнал действий, только для ADMIN/SUPERADMIN (проверяется в AuditLogService). */
@RestController
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final CurrentActor currentActor;

    @GetMapping("/api/v1/audit-log")
    public List<AuditLogResponse> recent() {
        return auditLogService.recent(currentActor.get()).stream().map(AuditLogResponse::from).toList();
    }

    @GetMapping("/api/v1/orders/{id}/audit-log")
    public List<AuditLogResponse> forOrder(@PathVariable UUID id, @RequestParam(defaultValue = "ORDER") String entityType) {
        return auditLogService.forEntity(entityType, id, currentActor.get()).stream().map(AuditLogResponse::from).toList();
    }
}
