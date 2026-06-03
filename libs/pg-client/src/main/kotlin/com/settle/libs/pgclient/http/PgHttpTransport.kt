package com.settle.libs.pgclient.http

enum class PgHttpMethod {
    GET,
    POST,
}

data class PgHttpRequest(
    val method: PgHttpMethod,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: Map<String, Any?> = emptyMap(),
)

data class PgHttpResponse(
    val statusCode: Int = 200,
    val body: Map<String, Any?>,
)

fun interface PgHttpTransport {
    fun execute(request: PgHttpRequest): PgHttpResponse
}
