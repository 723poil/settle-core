create schema if not exists migration;
create schema if not exists merchant;
create schema if not exists payment;
create schema if not exists settlement;

create table merchant.merchants (
    id uuid primary key,
    merchant_key varchar(64) not null unique,
    name varchar(120) not null,
    status varchar(30) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_merchants_status check (status in ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

create table merchant.pg_providers (
    id uuid primary key,
    code varchar(40) not null unique,
    name varchar(120) not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table merchant.pg_merchant_accounts (
    id uuid primary key,
    merchant_id uuid not null references merchant.merchants (id),
    pg_provider_id uuid not null references merchant.pg_providers (id),
    pg_mid varchar(120) not null,
    display_name varchar(120) not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_pg_merchant_accounts_mid unique (pg_provider_id, pg_mid)
);

create table payment.payment_transactions (
    id uuid primary key,
    merchant_id uuid not null references merchant.merchants (id),
    pg_provider_id uuid not null references merchant.pg_providers (id),
    pg_merchant_account_id uuid not null references merchant.pg_merchant_accounts (id),
    merchant_order_id varchar(120) not null,
    pg_transaction_id varchar(160) not null,
    transaction_type varchar(30) not null,
    status varchar(30) not null,
    amount numeric(19, 2) not null,
    currency char(3) not null,
    approved_at timestamptz,
    occurred_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_payment_transactions_type check (transaction_type in ('PAYMENT', 'CANCEL', 'PARTIAL_CANCEL')),
    constraint chk_payment_transactions_status check (status in ('REQUESTED', 'APPROVED', 'FAILED', 'CANCELED')),
    constraint uq_payment_transactions_pg unique (pg_provider_id, pg_transaction_id)
);

create table payment.payment_events (
    id uuid primary key,
    payment_transaction_id uuid not null references payment.payment_transactions (id),
    event_type varchar(40) not null,
    event_status varchar(40) not null,
    pg_event_id varchar(160),
    amount numeric(19, 2) not null,
    currency char(3) not null,
    occurred_at timestamptz not null,
    raw_payload jsonb,
    created_at timestamptz not null default now()
);

create table settlement.settlement_batches (
    id uuid primary key,
    pg_provider_id uuid not null references merchant.pg_providers (id),
    settlement_date date not null,
    status varchar(30) not null,
    gross_amount numeric(19, 2) not null,
    fee_amount numeric(19, 2) not null,
    tax_amount numeric(19, 2) not null,
    net_amount numeric(19, 2) not null,
    currency char(3) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_settlement_batches_status check (status in ('READY', 'CONFIRMED', 'PAID', 'CANCELED')),
    constraint uq_settlement_batches_provider_date unique (pg_provider_id, settlement_date)
);

create table settlement.settlement_lines (
    id uuid primary key,
    settlement_batch_id uuid not null references settlement.settlement_batches (id),
    payment_transaction_id uuid not null references payment.payment_transactions (id),
    merchant_id uuid not null references merchant.merchants (id),
    gross_amount numeric(19, 2) not null,
    fee_amount numeric(19, 2) not null,
    tax_amount numeric(19, 2) not null,
    net_amount numeric(19, 2) not null,
    currency char(3) not null,
    status varchar(30) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_settlement_lines_status check (status in ('READY', 'HELD', 'CONFIRMED', 'PAID', 'CANCELED')),
    constraint uq_settlement_lines_transaction unique (settlement_batch_id, payment_transaction_id)
);

create index idx_payment_transactions_merchant_order on payment.payment_transactions (merchant_id, merchant_order_id);
create index idx_payment_transactions_occurred_at on payment.payment_transactions (occurred_at);
create index idx_payment_events_transaction on payment.payment_events (payment_transaction_id, occurred_at);
create index idx_settlement_lines_merchant on settlement.settlement_lines (merchant_id, status);
