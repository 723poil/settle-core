alter table merchant.pg_merchant_accounts
    add column pg_product varchar(60);

update merchant.pg_merchant_accounts
set pg_product = 'payment'
where pg_product is null;

alter table merchant.pg_merchant_accounts
    alter column pg_product set not null;

alter table merchant.pg_merchant_accounts
    drop constraint uq_pg_merchant_accounts_mid;

alter table merchant.pg_merchant_accounts
    add constraint uq_pg_merchant_accounts_mid unique (pg_provider_id, pg_product, pg_mid);

alter table payment.payment_transactions
    add column pg_product varchar(60);

update payment.payment_transactions
set pg_product = 'payment'
where pg_product is null;

alter table payment.payment_transactions
    alter column pg_product set not null;

alter table payment.payment_transactions
    drop constraint uq_payment_transactions_pg;

alter table payment.payment_transactions
    add constraint uq_payment_transactions_pg unique (pg_provider_id, pg_product, pg_transaction_id);
