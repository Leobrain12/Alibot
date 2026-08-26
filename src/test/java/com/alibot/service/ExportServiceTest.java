package com.alibot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibot.domain.Master;
import com.alibot.domain.Order;
import com.alibot.domain.OrderStatus;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * ТЗ п.80 — экспорт заказов. Проверяем не просто "не падает", а что реально записанные байты
 * читаются обратно (CSV — построчно, XLSX — через сам POI) и содержат ожидаемые значения,
 * включая кириллицу (см. историю сессии — BOM для CSV добавлен именно из-за прошлых проблем
 * с кодировкой при просмотре кириллических данных в Excel/curl).
 */
class ExportServiceTest {

    private final ExportService exportService = new ExportService();

    private Order sampleOrder() {
        Master master = Master.builder().id(UUID.randomUUID()).name("Иванов И.И.").build();
        return Order.builder()
                .id(UUID.randomUUID())
                .number(1042L)
                .status(OrderStatus.PAID)
                .customerName("Петров, \"клиент\"")
                .customerPhone("+79990001122")
                .applianceType("Холодильник")
                .brand("Bosch")
                .address("ул. Ленина, 1")
                .visitDate(LocalDate.of(2026, 3, 5))
                .timeFrom(LocalTime.of(10, 0))
                .timeTo(LocalTime.of(12, 0))
                .master(master)
                .finalPrice(new BigDecimal("2500.00"))
                .amountPaid(new BigDecimal("2500.00"))
                .build();
    }

    @Test
    void csvRoundTripsHeaderAndEscapedRow() {
        byte[] bytes = exportService.toCsv(List.of(sampleOrder()));
        String text = new String(bytes, StandardCharsets.UTF_8);

        assertThat(text).startsWith("﻿Номер,Статус,Клиент");
        String[] lines = text.split("\r\n"); // split() drops the trailing empty segment after the last \r\n
        assertThat(lines).hasSize(2);
        assertThat(lines[1]).contains("1042", "PAID", "\"Петров, \"\"клиент\"\"\"", "Иванов И.И.", "2500");
    }

    @Test
    void xlsxRoundTripsThroughPoi() throws IOException {
        byte[] bytes = exportService.toXlsx(List.of(sampleOrder()));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Номер");

            Row data = sheet.getRow(1);
            assertThat(data.getCell(0).getNumericCellValue()).isEqualTo(1042.0);
            assertThat(data.getCell(1).getStringCellValue()).isEqualTo("PAID");
            assertThat(data.getCell(2).getStringCellValue()).isEqualTo("Петров, \"клиент\"");
            assertThat(data.getCell(11).getStringCellValue()).isEqualTo("Иванов И.И.");
            assertThat(data.getCell(13).getNumericCellValue()).isEqualTo(2500.0);
        }
    }
}
