package com.alibot.service;

import com.alibot.domain.Order;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * ТЗ п.80 — экспорт заказов в CSV/XLSX. Столбцы — прямое подмножество полей Order (ТЗ п.12-13),
 * без служебных (id, version) и без данных, не нужных вне системы (part_number и т.п. оставлены,
 * т.к. это отчёт для самого бизнеса, не для клиента).
 */
@Service
public class ExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] HEADERS = {
            "Номер", "Статус", "Клиент", "Телефон", "Техника", "Бренд", "Модель",
            "Адрес", "Дата визита", "Время с", "Время до", "Мастер",
            "Оценка", "Итог", "Себестоимость запчастей", "Выплата мастеру", "Оплачено",
            "Создан", "Завершён"
    };

    public byte[] toCsv(List<Order> orders) {
        StringBuilder sb = new StringBuilder();
        // BOM — иначе Excel по умолчанию открывает UTF-8 CSV с кириллицей как мусор (проверено
        // на прошлых шагах этой сессии: похожая проблема с кодировкой при curl/Invoke-RestMethod).
        sb.append('﻿');
        sb.append(String.join(",", HEADERS)).append("\r\n");
        for (Order o : orders) {
            sb.append(csvRow(o)).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvRow(Order o) {
        Object[] values = {
                o.getNumber(), o.getStatus(), o.getCustomerName(), o.getCustomerPhone(),
                o.getApplianceType(), o.getBrand(), o.getModel(), o.getAddress(),
                o.getVisitDate() == null ? null : DATE_FMT.format(o.getVisitDate()),
                o.getTimeFrom(), o.getTimeTo(),
                o.getMaster() == null ? null : o.getMaster().getName(),
                o.getEstimatedPrice(), o.getFinalPrice(), o.getPartsCost(), o.getMasterPayout(), o.getAmountPaid(),
                o.getCreatedAt(), o.getCompletedAt()
        };
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(csvEscape(values[i]));
        }
        return row.toString();
    }

    private String csvEscape(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public byte[] toXlsx(List<Order> orders) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Заказы");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int rowIdx = 1;
            for (Order o : orders) {
                Row row = sheet.createRow(rowIdx++);
                setCells(row, o);
            }
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось собрать XLSX-файл", e);
        }
    }

    private void setCells(Row row, Order o) {
        int i = 0;
        setCell(row.createCell(i++), o.getNumber());
        setCell(row.createCell(i++), o.getStatus().name());
        setCell(row.createCell(i++), o.getCustomerName());
        setCell(row.createCell(i++), o.getCustomerPhone());
        setCell(row.createCell(i++), o.getApplianceType());
        setCell(row.createCell(i++), o.getBrand());
        setCell(row.createCell(i++), o.getModel());
        setCell(row.createCell(i++), o.getAddress());
        setCell(row.createCell(i++), o.getVisitDate() == null ? null : DATE_FMT.format(o.getVisitDate()));
        setCell(row.createCell(i++), o.getTimeFrom() == null ? null : o.getTimeFrom().toString());
        setCell(row.createCell(i++), o.getTimeTo() == null ? null : o.getTimeTo().toString());
        setCell(row.createCell(i++), o.getMaster() == null ? null : o.getMaster().getName());
        setCell(row.createCell(i++), o.getEstimatedPrice());
        setCell(row.createCell(i++), o.getFinalPrice());
        setCell(row.createCell(i++), o.getPartsCost());
        setCell(row.createCell(i++), o.getMasterPayout());
        setCell(row.createCell(i++), o.getAmountPaid());
        setCell(row.createCell(i++), o.getCreatedAt() == null ? null : o.getCreatedAt().toString());
        setCell(row.createCell(i), o.getCompletedAt() == null ? null : o.getCompletedAt().toString());
    }

    private void setCell(Cell cell, String value) {
        if (value != null) {
            cell.setCellValue(value);
        }
    }

    private void setCell(Cell cell, Number value) {
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }
}
