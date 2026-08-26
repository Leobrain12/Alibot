package com.alibot.service.dto;

/** ТЗ п.10-11 — сырой лид, как его присылает сайт/CRM: минимум контакт, остальное необязательно. */
public record SubmitLeadCommand(
        String customerName,
        String customerPhone,
        String applianceType,
        String comment,
        String source,
        String externalId
) {
}
