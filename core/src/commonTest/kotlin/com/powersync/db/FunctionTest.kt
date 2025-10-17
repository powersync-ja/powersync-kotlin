package com.powersync.db

import io.kotest.assertions.throwables.shouldThrow
import kotlinx.coroutines.CancellationException
import kotlin.test.Test

class FunctionTest {
    @Test
    fun `runWrapped reports exceptions as powersync exception`() {
        shouldThrow<PowerSyncException> {
            runWrapped {
                error("test")
            }
        }
    }

    @Test
    fun `runWrapped does not wrap cancellation exceptions`() {
        shouldThrow<CancellationException> {
            runWrapped {
                throw CancellationException("test")
            }
        }
    }
}
