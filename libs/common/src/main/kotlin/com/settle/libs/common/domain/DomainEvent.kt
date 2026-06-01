package com.settle.libs.common.domain

import java.time.Instant

interface DomainEvent {
    val occurredAt: Instant
}
