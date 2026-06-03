package com.settle.libs.pgclient.support

import com.settle.libs.pgclient.PgMoney
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime

internal fun Map<String, Any?>.stringValue(name: String): String =
    requireNotNull(this[name]) { "PG response field '$name' is missing" }.toString()

internal fun Map<String, Any?>.optionalStringValue(name: String): String? = this[name]?.toString()

internal fun Map<String, Any?>.money(
    amountName: String,
    currencyName: String,
): PgMoney = PgMoney(decimalValue(amountName), stringValue(currencyName))

internal fun Map<String, Any?>.decimalValue(name: String): BigDecimal =
    when (val value = requireNotNull(this[name]) { "PG response field '$name' is missing" }) {
        is BigDecimal -> value
        is Number -> BigDecimal(value.toString())
        else -> value.toString().toBigDecimal()
    }

internal fun String.toInstantFromPg(): Instant =
    if (endsWith("Z")) {
        Instant.parse(this)
    } else {
        OffsetDateTime.parse(this).toInstant()
    }

internal fun BigDecimal.toPlainAmount(): BigDecimal = stripTrailingZeros().toPlainString().toBigDecimal()

internal fun BigDecimal.toPlainAmountString(): String = setScale(2).toPlainString()

@Suppress("UNCHECKED_CAST")
internal fun Any?.asMap(): Map<String, Any?> = this as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
internal fun Any?.asMapList(): List<Map<String, Any?>> = this as List<Map<String, Any?>>
