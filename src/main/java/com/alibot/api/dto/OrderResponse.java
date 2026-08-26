package com.alibot.api.dto;

import com.alibot.domain.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** DTO вместо отдачи JPA-сущности напрямую (избегаем проблем ленивой загрузки/переэкспозиции). */
public record OrderResponse(
        UUID id,
        Long number,
        String status,
        String customerName,
        String customerPhone,
        String applianceType,
        String brand,
        String model,
        String symptom,
        String description,
        String address,
        LocalDate visitDate,
        LocalTime timeFrom,
        LocalTime timeTo,
        UUID masterId,
        String masterName,
        BigDecimal estimatedPrice,
        BigDecimal finalPrice,
        BigDecimal laborPrice,
        BigDecimal partsSellPrice,
        BigDecimal partsCost,
        BigDecimal masterPayout,
        BigDecimal amountPaid,
        BigDecimal amountDue,
        String adminComment,
        String masterComment,
        String cancelReason,
        String partName,
        String partNumber,
        UUID warrantyParentOrderId,
        Instant createdAt,
        Instant completedAt
) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getId(), o.getNumber(), o.getStatus().name(),
                o.getCustomerName(), o.getCustomerPhone(),
                o.getApplianceType(), o.getBrand(), o.getModel(), o.getSymptom(), o.getDescription(),
                o.getAddress(), o.getVisitDate(), o.getTimeFrom(), o.getTimeTo(),
                o.getMaster() != null ? o.getMaster().getId() : null,
                o.getMaster() != null ? o.getMaster().getName() : null,
                o.getEstimatedPrice(), o.getFinalPrice(), o.getLaborPrice(), o.getPartsSellPrice(),
                o.getPartsCost(), o.getMasterPayout(), o.getAmountPaid(), o.amountDue(),
                o.getAdminComment(), o.getMasterComment(), o.getCancelReason(),
                o.getPartName(), o.getPartNumber(), o.getWarrantyParentOrderId(),
                o.getCreatedAt(), o.getCompletedAt()
        );
    }
}
