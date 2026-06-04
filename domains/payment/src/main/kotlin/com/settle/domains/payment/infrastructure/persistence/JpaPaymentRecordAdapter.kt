package com.settle.domains.payment.infrastructure.persistence

import com.settle.domains.merchant.persistence.MerchantEntity
import com.settle.domains.merchant.persistence.PgMerchantAccountEntity
import com.settle.domains.merchant.persistence.PgProviderEntity
import com.settle.domains.payment.application.port.persistence.PaymentPersistenceRecordPort
import com.settle.domains.payment.domain.model.AuthorizedPayment
import com.settle.domains.payment.domain.model.PaymentStatus
import com.settle.domains.payment.domain.model.PaymentTransactionSnapshot
import com.settle.domains.payment.domain.model.PreparedPayment
import com.settle.domains.payment.infrastructure.persistence.entity.PaymentEventEntity
import com.settle.domains.payment.infrastructure.persistence.entity.PaymentTransactionEntity
import com.settle.domains.payment.infrastructure.persistence.entity.PaymentTransactionStatusEntity
import com.settle.domains.payment.infrastructure.persistence.entity.PaymentTransactionTypeEntity
import com.settle.domains.payment.infrastructure.persistence.repository.PaymentEventJpaRepository
import com.settle.domains.payment.infrastructure.persistence.repository.PaymentTransactionJpaRepository
import jakarta.persistence.EntityManager
import org.springframework.transaction.annotation.Transactional

class JpaPaymentRecordAdapter(
    private val entityManager: EntityManager,
    private val paymentTransactions: PaymentTransactionJpaRepository,
    private val paymentEvents: PaymentEventJpaRepository,
) : PaymentPersistenceRecordPort {
    override fun findPaymentByIdempotencyKey(idempotencyKey: String): PaymentTransactionSnapshot? =
        paymentTransactions.findByIdempotencyKey(idempotencyKey)?.toSnapshot()

    @Transactional
    override fun recordPreparedPayment(payment: PreparedPayment) {
        val transaction =
            paymentTransactions.save(
                PaymentTransactionEntity(
                    idempotencyKey = payment.idempotencyKey,
                    merchant = entityManager.getReference(MerchantEntity::class.java, payment.merchantId),
                    pgProvider = entityManager.getReference(PgProviderEntity::class.java, payment.pgProviderId),
                    pgProduct = payment.pgProduct.code,
                    pgMerchantAccount = entityManager.getReference(PgMerchantAccountEntity::class.java, payment.pgMerchantAccountId),
                    merchantOrderId = payment.merchantOrderId,
                    pgTransactionId = payment.pgTransactionId,
                    transactionType = PaymentTransactionTypeEntity.PAYMENT,
                    status = payment.status.toEntityStatus(),
                    amount = payment.amount,
                    currency = payment.currency,
                    occurredAt = payment.occurredAt,
                ),
            )

        paymentEvents.save(
            PaymentEventEntity(
                paymentTransaction = transaction,
                eventType = payment.event.type.name,
                eventStatus = payment.event.status,
                pgEventId = payment.event.pgEventId,
                amount = payment.event.amount,
                currency = payment.event.currency,
                occurredAt = payment.event.occurredAt,
                rawPayload = payment.event.rawPayload,
            ),
        )
    }

    @Transactional
    override fun recordAuthorizedPayment(payment: AuthorizedPayment) {
        val transaction =
            paymentTransactions.findByIdempotencyKey(payment.idempotencyKey)
                ?: throw PaymentTransactionEntityNotFoundException(payment.idempotencyKey)

        transaction.pgTransactionId = payment.pgTransactionId
        transaction.status = payment.status.toEntityStatus()
        transaction.amount = payment.amount
        transaction.currency = payment.currency
        transaction.approvedAt = payment.approvedAt

        paymentEvents.save(
            PaymentEventEntity(
                paymentTransaction = transaction,
                eventType = payment.event.type.name,
                eventStatus = payment.event.status,
                pgEventId = payment.event.pgEventId,
                amount = payment.event.amount,
                currency = payment.event.currency,
                occurredAt = payment.event.occurredAt,
                rawPayload = payment.event.rawPayload,
            ),
        )
    }

    private fun PaymentStatus.toEntityStatus(): PaymentTransactionStatusEntity =
        when (this) {
            PaymentStatus.REQUESTED -> PaymentTransactionStatusEntity.REQUESTED
            PaymentStatus.APPROVED -> PaymentTransactionStatusEntity.APPROVED
            PaymentStatus.CANCELED -> PaymentTransactionStatusEntity.CANCELED
            PaymentStatus.FAILED -> PaymentTransactionStatusEntity.FAILED
        }

    private fun PaymentTransactionEntity.toSnapshot(): PaymentTransactionSnapshot =
        PaymentTransactionSnapshot(
            idempotencyKey = idempotencyKey,
            merchantId = merchant.id,
            pgProviderId = pgProvider.id,
            pgMerchantAccountId = pgMerchantAccount.id,
            merchantKey = merchant.merchantKey,
            pgProvider =
                com.settle.libs.pgclient
                    .PgProvider(pgProvider.code),
            pgProduct =
                com.settle.libs.pgclient
                    .PgPaymentProduct(pgProduct),
            pgMid = pgMerchantAccount.pgMid,
            merchantOrderId = merchantOrderId,
            pgTransactionId = pgTransactionId,
            status = status.toPaymentStatus(),
            amount = amount,
            currency = currency,
        )

    private fun PaymentTransactionStatusEntity.toPaymentStatus(): PaymentStatus =
        when (this) {
            PaymentTransactionStatusEntity.REQUESTED -> PaymentStatus.REQUESTED
            PaymentTransactionStatusEntity.APPROVED -> PaymentStatus.APPROVED
            PaymentTransactionStatusEntity.CANCELED -> PaymentStatus.CANCELED
            PaymentTransactionStatusEntity.FAILED -> PaymentStatus.FAILED
        }
}

class PaymentTransactionEntityNotFoundException(
    idempotencyKey: String,
) : RuntimeException("Payment transaction entity not found for idempotency key '$idempotencyKey'")
