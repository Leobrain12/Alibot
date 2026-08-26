package com.alibot.api.controller;

import com.alibot.api.security.CurrentActor;
import com.alibot.domain.Order;
import com.alibot.domain.OrderStatus;
import com.alibot.service.ExportService;
import com.alibot.service.OrderService;
import com.alibot.service.exception.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ТЗ п.80 — экспорт заказов за период в CSV или XLSX, только ADMIN/SUPERADMIN
 *  (проверяется в OrderService.exportData). Тот же фильтр по периоду, что и в /api/v1/stats. */
@RestController
@RequiredArgsConstructor
public class ExportController {

    private static final Set<String> ALLOWED_FORMATS = Set.of("csv", "xlsx");
    private static final int MAX_RANGE_DAYS = 366;

    private final OrderService orderService;
    private final ExportService exportService;
    private final CurrentActor currentActor;

    @GetMapping("/api/v1/orders/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "csv") String format,
                                          @RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to,
                                          @RequestParam(required = false) OrderStatus status) {
        String normalizedFormat = format.toLowerCase();
        if (!ALLOWED_FORMATS.contains(normalizedFormat)) {
            throw new ValidationException("Неизвестный формат экспорта: " + format + " (доступны csv, xlsx)");
        }
        Instant f = from != null ? Instant.parse(from) : Instant.now().truncatedTo(ChronoUnit.DAYS).minus(30, ChronoUnit.DAYS);
        Instant t = to != null ? Instant.parse(to) : Instant.now();
        if (t.isBefore(f)) {
            throw new ValidationException("Дата «до» раньше даты «от»");
        }
        // Без лимита эндпоинт мог бы вытянуть в память и отрендерить в один ответ весь архив
        // заказов за всё время существования системы по одному запросу.
        if (Duration.between(f, t).toDays() > MAX_RANGE_DAYS) {
            throw new ValidationException("Период экспорта не может превышать %d дней".formatted(MAX_RANGE_DAYS));
        }
        List<Order> orders = orderService.exportData(f, t, status, currentActor.get());

        boolean xlsx = "xlsx".equals(normalizedFormat);
        byte[] body = xlsx ? exportService.toXlsx(orders) : exportService.toCsv(orders);
        String filename = "alibot-orders-" + f.toString().substring(0, 10) + "_" + t.toString().substring(0, 10)
                + (xlsx ? ".xlsx" : ".csv");
        MediaType type = xlsx
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv");

        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
