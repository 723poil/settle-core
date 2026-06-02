package com.settle.libs.common.id

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UuidV7Tests {
    @Test
    fun generatesUuidV7() {
        val uuid = UuidV7.generate()

        assertEquals(7, uuid.version())
        assertEquals(2, uuid.variant())
    }

    @Test
    fun generatesUniqueValues() {
        val first = UuidV7.generate()
        val second = UuidV7.generate()

        assertNotEquals(first, second)
    }

    @Test
    fun generatesTimeOrderedValues() {
        val values = List(10) { UuidV7.generate().toString() }

        assertTrue(values.zipWithNext().all { (previous, next) -> previous < next })
    }
}
