-- Alibot core schema. Targets PostgreSQL; the H2 "dev" profile runs in
-- MODE=PostgreSQL so this same script applies unchanged in both environments.

create table users (
    id uuid primary key,
    telegram_user_id bigint not null unique,
    role varchar(20) not null,
    name varchar(255) not null,
    phone varchar(50),
    is_active boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table masters (
    id uuid primary key,
    user_id uuid not null unique references users(id),
    name varchar(255) not null,
    phone varchar(50),
    status varchar(20) not null default 'ACTIVE',
    commission_type varchar(20) not null default 'MANUAL',
    commission_value numeric(12,2),
    is_active boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table master_appliance_types (
    master_id uuid not null references masters(id) on delete cascade,
    appliance_type varchar(255) not null
);

create table master_brands (
    master_id uuid not null references masters(id) on delete cascade,
    brand varchar(255) not null
);

create table master_geo_zones (
    master_id uuid not null references masters(id) on delete cascade,
    geo_zone varchar(255) not null
);

create sequence order_number_seq start with 1000 increment by 1;

create table orders (
    id uuid primary key,
    number bigint not null unique,
    lead_id varchar(100),
    crm_id varchar(100),
    source varchar(100),
    customer_name varchar(255) not null,
    customer_phone varchar(50) not null,
    appliance_type varchar(255) not null,
    brand varchar(255),
    model varchar(255),
    symptom varchar(1000) not null,
    description varchar(2000),
    address varchar(1000) not null,
    address_lat double precision,
    address_lon double precision,
    visit_date date not null,
    time_from time not null,
    time_to time not null,
    master_id uuid references masters(id),
    status varchar(24) not null default 'NEW',
    estimated_price numeric(12,2),
    final_price numeric(12,2),
    labor_price numeric(12,2),
    parts_sell_price numeric(12,2),
    parts_cost numeric(12,2),
    master_payout numeric(12,2),
    amount_paid numeric(12,2),
    admin_comment varchar(2000),
    master_comment varchar(2000),
    cancel_reason varchar(500),
    warranty_parent_order_id uuid references orders(id),
    part_name varchar(255),
    part_number varchar(100),
    part_estimated_cost numeric(12,2),
    created_by uuid not null references users(id),
    created_at timestamp not null,
    updated_at timestamp not null,
    completed_at timestamp,
    accepted_at timestamp,
    on_the_way_at timestamp,
    arrived_at timestamp
);

create index idx_orders_status on orders(status);
create index idx_orders_master on orders(master_id);
create index idx_orders_visit_date on orders(visit_date);

create table order_status_history (
    id uuid primary key,
    order_id uuid not null references orders(id),
    old_status varchar(24),
    new_status varchar(24) not null,
    changed_by_user_id uuid not null references users(id),
    comment varchar(1000),
    created_at timestamp not null
);
create index idx_osh_order on order_status_history(order_id);

create table order_visits (
    id uuid primary key,
    order_id uuid not null references orders(id),
    visit_number int not null,
    visit_date date not null,
    time_from time not null,
    time_to time not null,
    master_id uuid references masters(id),
    status varchar(24) not null,
    reason varchar(500),
    created_at timestamp not null,
    completed_at timestamp
);
create index idx_visits_order on order_visits(order_id);

create table contact_attempts (
    id uuid primary key,
    order_id uuid not null references orders(id),
    user_id uuid not null references users(id),
    attempted_at timestamp not null,
    result varchar(20) not null,
    comment varchar(500)
);
create index idx_contact_attempts_order on contact_attempts(order_id);

create table work_reports (
    id uuid primary key,
    order_id uuid not null references orders(id),
    visit_id uuid references order_visits(id),
    master_id uuid not null references masters(id),
    failure_reason varchar(1000),
    work_description varchar(2000) not null,
    labor_price numeric(12,2) not null,
    parts_sell_price numeric(12,2) not null,
    parts_cost numeric(12,2),
    final_price numeric(12,2) not null,
    master_payout numeric(12,2),
    comment varchar(1000),
    created_at timestamp not null,
    confirmed_at timestamp
);
create index idx_work_reports_order on work_reports(order_id);

create table order_media (
    id uuid primary key,
    order_id uuid not null references orders(id),
    visit_id uuid references order_visits(id),
    work_report_id uuid references work_reports(id),
    uploaded_by_user_id uuid not null references users(id),
    media_type varchar(10) not null,
    stage varchar(20) not null,
    telegram_file_id varchar(255) not null,
    telegram_file_unique_id varchar(255),
    mime_type varchar(100),
    original_file_name varchar(500),
    file_size bigint,
    duration_seconds int,
    width int,
    height int,
    storage_path varchar(1000),
    caption varchar(1000),
    created_at timestamp not null
);
create index idx_order_media_order on order_media(order_id);

create table payments (
    id uuid primary key,
    order_id uuid not null references orders(id),
    amount numeric(12,2) not null,
    payment_type varchar(50),
    received_by uuid references users(id),
    created_at timestamp not null
);
create index idx_payments_order on payments(order_id);

create table conversation_states (
    id uuid primary key,
    chat_id bigint not null,
    telegram_user_id bigint not null,
    scenario varchar(40) not null,
    step varchar(40) not null,
    draft_json text,
    related_order_id uuid,
    created_at timestamp not null,
    updated_at timestamp not null,
    expired boolean not null default false
);
create index idx_conv_state_chat on conversation_states(chat_id, expired);

create table processed_telegram_updates (
    update_id bigint primary key,
    processed_at timestamp not null
);
