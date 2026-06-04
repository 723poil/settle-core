package com.settle.domains.payment.infrastructure.persistence.repository

import com.settle.domains.payment.infrastructure.persistence.entity.PaymentEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentEventJpaRepository : JpaRepository<PaymentEventEntity, UUID>
