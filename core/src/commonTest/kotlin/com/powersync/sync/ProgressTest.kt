package com.powersync.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressTest {

    @Test
    fun reportsFraction() {
        assertEquals(0.0, ProgressWithOperations(0, 10).fraction)
        assertEquals(0.5, ProgressWithOperations(5, 10).fraction)
        assertEquals(1.0, ProgressWithOperations(10, 10).fraction)

        assertEquals(0.0, ProgressWithOperations(0, 0).fraction)
    }
}
