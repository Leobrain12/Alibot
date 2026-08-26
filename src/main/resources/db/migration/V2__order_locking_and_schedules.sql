-- ТЗ п.112 — optimistic locking для заказа (см. Order#version).
alter table orders add column version bigint not null default 0;

-- ТЗ п.83/84 — метки "уже напомнили" / "уже эскалировали недозвон принятия", чтобы
-- плановые задачи (OrderTimersScheduler) не слали одно и то же уведомление повторно.
alter table orders add column reminder_sent_at timestamp;
alter table orders add column accept_timeout_notified_at timestamp;
