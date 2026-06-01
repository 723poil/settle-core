package com.settle.libs.pgclient

@JvmInline
value class PgProvider(
    val code: String,
) {
    init {
        require(code.isNotBlank()) { "PG provider code must not be blank" }
    }
}
