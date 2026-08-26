package com.alibot.api.controller;

import com.alibot.api.dto.MasterResponse;
import com.alibot.api.security.CurrentActor;
import com.alibot.domain.MasterStatus;
import com.alibot.service.MasterService;
import com.alibot.service.StatsService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MasterController {

    private final MasterService masterService;
    private final StatsService statsService;
    private final CurrentActor currentActor;

    @GetMapping("/api/v1/masters")
    public List<MasterResponse> list() {
        return masterService.list(currentActor.get()).stream().map(MasterResponse::from).toList();
    }

    @GetMapping("/api/v1/masters/{id}")
    public MasterResponse get(@PathVariable UUID id) {
        return MasterResponse.from(masterService.getById(id, currentActor.get()));
    }

    /** ТЗ F8 "edit/deactivate" — статус/активность мастера, только SUPERADMIN. */
    @PatchMapping("/api/v1/masters/{id}")
    public MasterResponse update(@PathVariable UUID id, @RequestBody UpdateMasterRequest req) {
        return MasterResponse.from(masterService.update(id, req.status(), req.active(), currentActor.get()));
    }

    public record UpdateMasterRequest(MasterStatus status, Boolean active) {
    }

    @GetMapping("/api/v1/masters/{id}/stats")
    public StatsService.MasterStats stats(@PathVariable UUID id,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        Instant f = from != null ? Instant.parse(from) : Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant t = to != null ? Instant.parse(to) : Instant.now();
        return statsService.masterStats(id, f, t, currentActor.get());
    }
}
