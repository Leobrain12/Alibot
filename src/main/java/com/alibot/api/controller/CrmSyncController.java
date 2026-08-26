package com.alibot.api.controller;

import com.alibot.api.dto.CrmSyncItemResponse;
import com.alibot.api.security.CurrentActor;
import com.alibot.service.CrmSyncService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** ТЗ п.85-87 — видимость и ручной повтор для событий CRM-синхронизации, которые не удалось
 *  доставить (только ADMIN/SUPERADMIN, проверяется в CrmSyncService). */
@RestController
@RequiredArgsConstructor
public class CrmSyncController {

    private final CrmSyncService crmSyncService;
    private final CurrentActor currentActor;

    @GetMapping("/api/v1/crm-sync/failed")
    public List<CrmSyncItemResponse> failed() {
        return crmSyncService.listFailed(currentActor.get()).stream().map(CrmSyncItemResponse::from).toList();
    }

    @PostMapping("/api/v1/crm-sync/{id}/retry")
    public ResponseEntity<Void> retry(@PathVariable UUID id) {
        crmSyncService.retry(id, currentActor.get());
        return ResponseEntity.ok().build();
    }
}
