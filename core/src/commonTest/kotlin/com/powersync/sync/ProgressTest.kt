package com.powersync.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressTest {
    @Test
    fun reportsFraction() {
        assertEquals(0.0f, ProgressWithOperations(0, 10).fraction)
        assertEquals(0.5f, ProgressWithOperations(5, 10).fraction)
        assertEquals(1.0f, ProgressWithOperations(10, 10).fraction)

        assertEquals(0.0f, ProgressWithOperations(0, 0).fraction)
    }
}
