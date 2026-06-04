alter table payment.payment_transactions
    add column idempotency_key varchar(120);

alter table payment.payment_transactions
    add constraint uq_payment_transactions_idempotency_key unique (idempotency_key);
