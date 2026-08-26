package com.alibot.api.controller;

import com.alibot.api.security.CurrentActor;
import com.alibot.service.StatsService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;
    private final CurrentActor currentActor;

    @GetMapping("/api/v1/stats")
    public StatsService.OverallStats overall(@RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to) {
        Instant f = from != null ? Instant.parse(from) : Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant t = to != null ? Instant.parse(to) : Instant.now();
        return statsService.overallStats(f, t, currentActor.get());
    }
}
