package com.alibot.api.controller;

import com.alibot.api.dto.ReferenceDataResponse;
import com.alibot.service.ReferenceDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** ТЗ п.18.1/132 — справочники отдаются backend'ом, не хардкодятся в клиентах (боте/Mini App). */
@RestController
@RequiredArgsConstructor
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    @GetMapping("/api/v1/reference-data")
    public ReferenceDataResponse get() {
        return ReferenceDataResponse.from(referenceDataService);
    }
}
