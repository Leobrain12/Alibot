package com.alibot.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Мелкие request-DTO для action-эндпоинтов OrderController — собраны в одном файле ради компактности. */
public final class Requests {
    private Requests() {
    }

    public record MasterIdRequest(UUID masterId) {
    }

    public record ReasonRequest(String reason) {
    }

    public record UpdateOrderRequest(String adminComment, String description) {
    }

    public record PaymentRequest(BigDecimal amount, String paymentType) {
    }
}
