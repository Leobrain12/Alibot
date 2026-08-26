-- ТЗ п.10-11 — Lead: маркетинговая заявка (сырой контакт с сайта/CRM/звонка), отдельно от Order
-- (уже квалифицированной задачи на ремонт). Order.lead_id — строковая ссылка на leads.id, не FK:
-- поле существовало раньше этой таблицы как nullable-заметка и продолжает так же работать для
-- заказов, созданных не через конвертацию лида (напрямую ботом/Mini App), поэтому FK было бы
-- неверным — не у каждого Order есть настоящий Lead.
create table leads (
    id uuid primary key,
    customer_name varchar(255) not null,
    customer_phone varchar(50) not null,
    appliance_type varchar(255),
    comment varchar,
    source varchar(100),
    external_id varchar(255),
    status varchar(16) not null,
    converted_order_id uuid references orders(id),
    reject_reason varchar,
    created_at timestamp not null,
    processed_at timestamp
);
create index idx_leads_status on leads(status);
