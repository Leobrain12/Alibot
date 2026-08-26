package com.alibot.api.dto;

import com.alibot.service.ReferenceDataService;
import java.util.List;

/** Форма ответа сохранена такой же, какой была у бывшего CatalogProperties — Mini App читает
 *  те же поля (applianceTypes, popularBrands и т.д.), источник данных сменился на БД незаметно. */
public record ReferenceDataResponse(
        List<String> applianceTypes,
        List<String> popularBrands,
        List<String> timeSlots,
        List<String> masterDeclineReasons,
        List<String> customerCancelReasons,
        List<String> rescheduleReasons
) {
    public static ReferenceDataResponse from(ReferenceDataService service) {
        return new ReferenceDataResponse(
                service.getApplianceTypes(),
                service.getPopularBrands(),
                service.getTimeSlots(),
                service.getMasterDeclineReasons(),
                service.getCustomerCancelReasons(),
                service.getRescheduleReasons());
    }
}
