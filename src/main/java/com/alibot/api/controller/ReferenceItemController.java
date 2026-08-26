package com.alibot.api.controller;

import com.alibot.api.dto.ReferenceItemResponse;
import com.alibot.api.security.CurrentActor;
import com.alibot.domain.ReferenceCategory;
import com.alibot.service.ReferenceDataService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** ТЗ п.132/5.1 — редактирование справочников, доступно только SUPERADMIN (проверяется в ReferenceDataService). */
@RestController
@RequiredArgsConstructor
public class ReferenceItemController {

    private final ReferenceDataService referenceDataService;
    private final CurrentActor currentActor;

    @GetMapping("/api/v1/reference-items")
    public List<ReferenceItemResponse> list() {
        return referenceDataService.listAll(currentActor.get()).stream().map(ReferenceItemResponse::from).toList();
    }

    @PostMapping("/api/v1/reference-items")
    public ReferenceItemResponse create(@RequestBody CreateRequest req) {
        return ReferenceItemResponse.from(
                referenceDataService.create(req.category(), req.value(), currentActor.get()));
    }

    public record CreateRequest(ReferenceCategory category, String value) {
    }

    @PatchMapping("/api/v1/reference-items/{id}")
    public ReferenceItemResponse update(@PathVariable UUID id, @RequestBody UpdateRequest req) {
        return ReferenceItemResponse.from(
                referenceDataService.update(id, req.value(), req.active(), req.sortOrder(), currentActor.get()));
    }

    public record UpdateRequest(String value, Boolean active, Integer sortOrder) {
    }
}
