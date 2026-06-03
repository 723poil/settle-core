package com.settle.libs.pgclient

sealed class PgPaymentClientException(
    message: String,
) : RuntimeException(message)

class DuplicatePgPaymentClientException(
    val route: PgPaymentRoute,
) : PgPaymentClientException("Duplicate PG payment client for route '${route.provider.code}:${route.product.code}'")

class PgPaymentClientNotFoundException(
    val route: PgPaymentRoute,
) : PgPaymentClientException("PG payment client not found for route '${route.provider.code}:${route.product.code}'")

class PgPaymentAccountNotFoundException(
    val route: PgPaymentRoute,
    val pgMid: String,
) : PgPaymentClientException("PG payment account '$pgMid' not found for route '${route.provider.code}:${route.product.code}'")

class PgPaymentOperationNotSupportedException(
    val route: PgPaymentRoute,
    val operation: String,
) : PgPaymentClientException("PG payment operation '$operation' is not supported for route '${route.provider.code}:${route.product.code}'")
