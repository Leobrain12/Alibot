-- ТЗ п.100 — срок хранения медиа. purged_at != null значит содержимое файла удалено с диска,
-- но сама запись остаётся (кто загрузил, когда, какого типа) — история не должна исчезать
-- вместе с файлом.
alter table order_media add column purged_at timestamp;
create index idx_order_media_retention on order_media(purged_at, created_at);
