package com.settle.libs.common.id

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

object UuidV7 {
    fun generate(): UUID = UuidCreator.getTimeOrderedEpoch()
}
