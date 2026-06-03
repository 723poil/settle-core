package com.settle.libs.infra

import com.settle.libs.pgclient.http.PgHttpMethod
import com.settle.libs.pgclient.http.PgHttpRequest
import com.settle.libs.pgclient.http.PgHttpResponse
import com.settle.libs.pgclient.http.PgHttpTransport
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.client.RestClient

class RestClientPgHttpTransport(
    private val restClient: RestClient,
) : PgHttpTransport {
    override fun execute(request: PgHttpRequest): PgHttpResponse {
        val response =
            when (request.method) {
                PgHttpMethod.GET -> {
                    restClient
                        .get()
                        .uri(request.url)
                        .headers { headers -> request.headers.forEach(headers::set) }
                        .retrieve()
                        .toEntity(MAP_TYPE)
                }

                PgHttpMethod.POST -> {
                    restClient
                        .post()
                        .uri(request.url)
                        .headers { headers -> request.headers.forEach(headers::set) }
                        .body(request.body)
                        .retrieve()
                        .toEntity(MAP_TYPE)
                }
            }

        return PgHttpResponse(
            statusCode = response.statusCode.value(),
            body = response.body ?: emptyMap(),
        )
    }

    private companion object {
        private val MAP_TYPE = object : ParameterizedTypeReference<Map<String, Any?>>() {}
    }
}
