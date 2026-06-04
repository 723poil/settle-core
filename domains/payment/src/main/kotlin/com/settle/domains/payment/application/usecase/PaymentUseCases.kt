package com.settle.domains.payment.application.usecase

import com.settle.domains.payment.application.port.PaymentRecordPort
import com.settle.domains.payment.application.port.PgMerchantAccountLookupPort
import com.settle.domains.payment.application.provider.PgPaymentProviderRegistry
import com.settle.domains.payment.domain.model.PreparedPayment
import com.settle.libs.pgclient.PgAuthorizeRequest
import com.settle.libs.pgclient.PgAuthorizeResponse
import com.settle.libs.pgclient.PgCancelRequest
import com.settle.libs.pgclient.PgCancelResponse
import com.settle.libs.pgclient.PgLookupRequest
import com.settle.libs.pgclient.PgLookupResponse
import com.settle.libs.pgclient.PgMoney
import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgPaymentRoute
import com.settle.libs.pgclient.PgPrepareRequest
import com.settle.libs.pgclient.PgPrepareResponse
import com.settle.libs.pgclient.PgProvider

class PreparePaymentUseCase(
    private val accounts: PgMerchantAccountLookupPort,
    private val records: PaymentRecordPort,
    private val providers: PgPaymentProviderRegistry,
) {
    fun prepare(command: PreparePaymentCommand): PgPrepareResponse {
        val account = accounts.getActiveAccount(command)
        val response =
            providers
                .get(PgPaymentRoute(account.pgProvider, account.pgProduct))
                .prepare(command.toPgPrepareRequest(account.pgMid))

        records.recordPreparedPayment(
            PreparedPayment.from(
                account = account,
                merchantOrderId = command.merchantOrderId,
                amount = command.amount.amount,
                currency = command.amount.currency,
                response = response,
            ),
        )

        return response
    }
}

class LookupPaymentUseCase(
    private val providers: PgPaymentProviderRegistry,
) {
    fun lookup(command: LookupPaymentCommand): PgLookupResponse = providers.get(command.route).lookup(command.request)
}

class AuthorizePaymentUseCase(
    private val providers: PgPaymentProviderRegistry,
) {
    fun authorize(command: AuthorizePaymentCommand): PgAuthorizeResponse = providers.get(command.route).authorize(command.request)
}

class CancelPaymentUseCase(
    private val providers: PgPaymentProviderRegistry,
) {
    fun cancel(command: CancelPaymentCommand): PgCancelResponse = providers.get(command.route).cancel(command.request)
}

data class PreparePaymentCommand(
    val merchantKey: String,
    val pgProvider: PgProvider,
    val pgProduct: PgPaymentProduct,
    val pgMid: String,
    val merchantOrderId: String,
    val orderName: String,
    val amount: PgMoney,
    val successUrl: String? = null,
    val failureUrl: String? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(merchantKey.isNotBlank()) { "merchant key must not be blank" }
        require(pgMid.isNotBlank()) { "PG MID must not be blank" }
        require(merchantOrderId.isNotBlank()) { "merchant order id must not be blank" }
        require(orderName.isNotBlank()) { "order name must not be blank" }
    }

    fun toPgPrepareRequest(pgMid: String): PgPrepareRequest =
        PgPrepareRequest(
            pgMid = pgMid,
            merchantOrderId = merchantOrderId,
            orderName = orderName,
            amount = amount,
            successUrl = successUrl,
            failureUrl = failureUrl,
            metadata = metadata,
        )
}

data class LookupPaymentCommand(
    val route: PgPaymentRoute,
    val request: PgLookupRequest,
)

data class AuthorizePaymentCommand(
    val route: PgPaymentRoute,
    val request: PgAuthorizeRequest,
)

data class CancelPaymentCommand(
    val route: PgPaymentRoute,
    val request: PgCancelRequest,
)
