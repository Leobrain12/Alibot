package com.alibot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * ТЗ п.132 — одна строка справочника (тип техники, бренд, слот, причина и т.п.), редактируемая
 * SUPERADMIN через интерфейс, а не только через правку конфига. Мягкое удаление через `active`,
 * как и везде в проекте — существующие заказы хранят значение как обычную строку, а не ссылку
 * на эту таблицу, так что скрытие/переименование пункта справочника не ломает историю.
 */
@Entity
@Table(name = "reference_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferenceItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReferenceCategory category;

    /** Java-свойство остаётся "value" (используется в производных запросах репозитория),
     *  колонка в БД называется иначе — см. V3__reference_items.sql. */
    @Column(name = "item_value", nullable = false)
    private String value;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
