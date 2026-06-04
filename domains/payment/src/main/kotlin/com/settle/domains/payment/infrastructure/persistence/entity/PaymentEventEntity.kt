package com.settle.domains.payment.infrastructure.persistence.entity

import com.settle.libs.common.id.UuidV7
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(schema = "payment", name = "payment_events")
open class PaymentEventEntity(
    @Id
    @Column(name = "id", nullable = false)
    open var id: UUID = UuidV7.generate(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_transaction_id", nullable = false)
    open var paymentTransaction: PaymentTransactionEntity,
    @Column(name = "event_type", nullable = false, length = 40)
    open var eventType: String,
    @Column(name = "event_status", nullable = false, length = 40)
    open var eventStatus: String,
    @Column(name = "pg_event_id", length = 160)
    open var pgEventId: String? = null,
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    open var amount: BigDecimal,
    @Column(name = "currency", nullable = false, length = 3)
    open var currency: String,
    @Column(name = "occurred_at", nullable = false)
    open var occurredAt: Instant,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    open var rawPayload: Map<String, Any?>? = null,
) {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    open lateinit var createdAt: Instant
        protected set

    override fun toString(): String = "PaymentEventEntity(id=$id, eventType=$eventType, eventStatus=$eventStatus)"
}
