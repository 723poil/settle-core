package com.settle.libs.pgclient

sealed class PgPaymentClientException(
    message: String,
) : RuntimeException(message)

class DuplicatePgPaymentClientException(
    val provider: PgProvider,
) : PgPaymentClientException("Duplicate PG payment client for provider '${provider.code}'")

class PgPaymentClientNotFoundException(
    val provider: PgProvider,
) : PgPaymentClientException("PG payment client not found for provider '${provider.code}'")
