-- ТЗ п.95 — общий журнал действий (в дополнение к order_status_history, которая покрывает
-- только смену статуса заказа). Колонки без явного varchar(N) — как и draft_json в V3, чтобы
-- не зависеть от длины конкретного текста; см. историю с "clob"/"value" в предыдущих миграциях.

create table audit_log (
    id uuid primary key,
    actor_user_id uuid,
    action varchar(40) not null,
    entity_type varchar(40) not null,
    entity_id uuid not null,
    old_value varchar,
    new_value varchar,
    created_at timestamp not null
);
create index idx_audit_log_entity on audit_log(entity_type, entity_id);
create index idx_audit_log_created_at on audit_log(created_at);
