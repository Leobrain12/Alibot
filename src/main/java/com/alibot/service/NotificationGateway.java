package com.alibot.service;

import com.alibot.domain.Lead;
import com.alibot.domain.Order;

/**
 * Порт: "нужно сообщить кому-то о событии по заказу". Единственная реализация — Telegram
 * (bot.TelegramNotificationGateway), но сервисный слой о Telegram ничего не знает — это и есть
 * граница, которая не даёт бизнес-логике просочиться в транспортный (bot/api) слой.
 */
public interface NotificationGateway {

    void masterAssigned(Order order);

    void masterAcceptedNotifyAdmin(Order order);

    void masterDeclinedNotifyAdmin(Order order, String reason);

    void orderChangedNotifyMaster(Order order, String changeSummary);

    void masterTransferredNotifyOldMaster(Order order);

    void partNeededNotifyAdmin(Order order, String partName, String comment);

    void orderCompletedNotifyAdmin(Order order);

    void orderPaidNotifyAdmin(Order order);

    void warrantyCreatedNotifyAdmin(Order order);

    void masterNotAcceptedTimeout(Order order, int minutes);

    /** ТЗ п.38 — рекомендация админу закрыть заказ после N недозвонов подряд. */
    void contactAttemptsExceededNotifyAdmin(Order order, int attempts);

    /** ТЗ п.10-11 — новый лид пришёл (с сайта/CRM/вручную) и ждёт обработки администратором. */
    void leadCreatedNotifyAdmin(Lead lead);

    void reminder(Order order, int minutesBefore);

    /** ТЗ п.79 — ежедневная сводка администратору. */
    void dailyDigest(StatsService.OverallStats stats);
}
