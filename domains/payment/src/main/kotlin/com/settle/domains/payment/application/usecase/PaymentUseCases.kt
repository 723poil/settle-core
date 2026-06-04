package com.settle.domains.payment.application.usecase

import com.settle.domains.payment.application.port.concurrency.PaymentConcurrencyLockPort
import com.settle.domains.payment.application.port.persistence.PaymentPersistenceRecordPort
import com.settle.domains.payment.application.port.persistence.PgMerchantAccountPersistenceLookupPort
import com.settle.domains.payment.application.provider.PgPaymentProviderRegistry
import com.settle.domains.payment.domain.model.AuthorizedPayment
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
    private val accounts: PgMerchantAccountPersistenceLookupPort,
    private val records: PaymentPersistenceRecordPort,
    private val locks: PaymentConcurrencyLockPort,
    private val providers: PgPaymentProviderRegistry,
) {
    fun prepare(command: PreparePaymentCommand): PgPrepareResponse =
        locks.withLock(PaymentLockOperation.PREPARE, command.idempotencyKey) {
            if (records.findPaymentByIdempotencyKey(command.idempotencyKey) != null) {
                throw DuplicatePaymentRequestException(PaymentLockOperation.PREPARE.redisKey(command.idempotencyKey))
            }

            prepareWithPg(command)
        }

    private fun prepareWithPg(command: PreparePaymentCommand): PgPrepareResponse {
        val account = accounts.getActiveAccount(command)
        val response =
            providers
                .get(PgPaymentRoute(account.pgProvider, account.pgProduct))
                .prepare(command.toPgPrepareRequest(account.pgMid))

        records.recordPreparedPayment(
            PreparedPayment.from(
                idempotencyKey = command.idempotencyKey,
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
    private val records: PaymentPersistenceRecordPort,
    private val locks: PaymentConcurrencyLockPort,
    private val providers: PgPaymentProviderRegistry,
) {
    fun authorize(command: AuthorizePaymentCommand): PgAuthorizeResponse =
        locks.withLock(PaymentLockOperation.AUTHORIZE, command.idempotencyKey) {
            val payment =
                records.findPaymentByIdempotencyKey(command.idempotencyKey)
                    ?: throw PaymentTransactionNotFoundException(command.idempotencyKey)
            val provider = providers.get(PgPaymentRoute(payment.pgProvider, payment.pgProduct))
            val request =
                PgAuthorizeRequest(
                    pgMid = payment.pgMid,
                    merchantOrderId = payment.merchantOrderId,
                    pgTransactionId = payment.pgTransactionId,
                    authorizationToken = command.authorizationToken,
                    amount = PgMoney(payment.amount, payment.currency),
                )
            val response = provider.authorize(request)

            try {
                records.recordAuthorizedPayment(AuthorizedPayment.from(payment, response))
            } catch (exception: RuntimeException) {
                if (response.status == com.settle.libs.pgclient.PgPaymentStatus.APPROVED) {
                    provider.cancel(
                        PgCancelRequest(
                            pgMid = payment.pgMid,
                            pgTransactionId = response.pgTransactionId,
                            cancelAmount = response.amount,
                            reason = "authorization persistence failed",
                            idempotencyKey = command.idempotencyKey,
                        ),
                    )
                }
                throw exception
            }

            response
        }
}

class CancelPaymentUseCase(
    private val locks: PaymentConcurrencyLockPort,
    private val providers: PgPaymentProviderRegistry,
) {
    fun cancel(command: CancelPaymentCommand): PgCancelResponse =
        locks.withLock(PaymentLockOperation.CANCEL, command.request.idempotencyKey) {
            providers.get(command.route).cancel(command.request)
        }
}

data class PreparePaymentCommand(
    val idempotencyKey: String,
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
        require(idempotencyKey.isNotBlank()) { "idempotency key must not be blank" }
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
    val idempotencyKey: String,
    val authorizationToken: String,
) {
    init {
        require(idempotencyKey.isNotBlank()) { "idempotency key must not be blank" }
        require(authorizationToken.isNotBlank()) { "authorization token must not be blank" }
    }
}

data class CancelPaymentCommand(
    val route: PgPaymentRoute,
    val request: PgCancelRequest,
)

enum class PaymentLockOperation(
    private val value: String,
) {
    PREPARE("prepare"),
    AUTHORIZE("authorize"),
    CANCEL("cancel"),
    ;

    fun redisKey(idempotencyKey: String): String = "payment:$value:$idempotencyKey"
}

class DuplicatePaymentRequestException(
    val lockKey: String,
) : RuntimeException("Payment request is already processing or recorded for idempotency key '$lockKey'")

class PaymentTransactionNotFoundException(
    idempotencyKey: String,
) : RuntimeException("Payment transaction not found for idempotency key '$idempotencyKey'")
