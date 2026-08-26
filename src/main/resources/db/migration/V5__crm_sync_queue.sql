-- ТЗ п.85-87 — очередь синхронизации заказов с CRM, с ретраями при сбое доставки.
create table crm_sync_queue (
    id uuid primary key,
    order_id uuid not null,
    event_type varchar(40) not null,
    payload varchar not null,
    status varchar(16) not null,
    attempts int not null default 0,
    last_error varchar,
    next_attempt_at timestamp not null,
    created_at timestamp not null,
    updated_at timestamp not null
);
create index idx_crm_sync_queue_status_next_attempt on crm_sync_queue(status, next_attempt_at);
create index idx_crm_sync_queue_order on crm_sync_queue(order_id);
