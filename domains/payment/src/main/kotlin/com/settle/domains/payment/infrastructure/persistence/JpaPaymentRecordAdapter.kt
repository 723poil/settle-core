package com.settle.domains.payment.infrastructure.persistence

import com.settle.domains.merchant.persistence.MerchantEntity
import com.settle.domains.merchant.persistence.PgMerchantAccountEntity
import com.settle.domains.merchant.persistence.PgProviderEntity
import com.settle.domains.payment.application.port.PaymentRecordPort
import com.settle.domains.payment.domain.model.PaymentStatus
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
) : PaymentRecordPort {
    @Transactional
    override fun recordPreparedPayment(payment: PreparedPayment) {
        val transaction =
            paymentTransactions.save(
                PaymentTransactionEntity(
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

    private fun PaymentStatus.toEntityStatus(): PaymentTransactionStatusEntity =
        when (this) {
            PaymentStatus.REQUESTED -> PaymentTransactionStatusEntity.REQUESTED
            PaymentStatus.APPROVED -> PaymentTransactionStatusEntity.APPROVED
            PaymentStatus.CANCELED -> PaymentTransactionStatusEntity.CANCELED
            PaymentStatus.FAILED -> PaymentTransactionStatusEntity.FAILED
        }
}
