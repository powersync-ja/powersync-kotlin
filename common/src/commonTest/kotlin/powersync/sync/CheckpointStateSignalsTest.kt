package powersync.sync

import com.powersync.ExperimentalCheckpointRequestsApi
import com.powersync.sync.CheckpointRequestException
import com.powersync.sync.CheckpointStateSignals
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCheckpointRequestsApi::class)
class CheckpointStateSignalsTest {
    private lateinit var signals: CheckpointStateSignals

    @BeforeTest
    fun setup() {
        signals = CheckpointStateSignals()
    }

    @Test
    fun markReadySuccess() =
        runTest {
            signals.markCheckpointsReady { }
            signals.waitForCheckpointRequestsReady()
        }

    @Test
    fun markReadyFailure() =
        runTest {
            shouldThrow<Exception> { signals.markCheckpointsReady { throw Exception("stub") } }
            shouldThrow<Exception> { signals.waitForCheckpointRequestsReady() }
        }

    @Test
    fun markDisconnected() =
        runTest {
            signals.disconnected()
            shouldThrow<CheckpointRequestException.Disconnected> { signals.waitForCheckpointRequestsReady() }
        }

    @Test
    fun supportsConcurrentWaiters() =
        runTest {
            val first = async { signals.waitForCheckpointRequestsReady() }
            val second = async { signals.waitForCheckpointRequestsReady() }
            yield()

            signals.markCheckpointsReady { }
            first.await()
            second.await()
        }

    @Test
    fun waitForCheckpointWaiterIsNotified() =
        runTest {
            val notified = async { signals.waitForCheckpointWaiter() }
            launch { signals.waitForCheckpointRequestsReady() }

            notified.await()
            signals.markCheckpointsReady { }
        }

    @Test
    fun doesNotNotifyWaiterWhenWakeDownloadLoopIsFalse() =
        runTest {
            val notified = async { signals.waitForCheckpointWaiter() }
            val waitReady = launch { signals.waitForCheckpointRequestsReady(wakeDownloadLoop = false) }

            testScheduler.runCurrent()
            notified.isCompleted shouldBe false

            waitReady.cancel()
            notified.cancel()
        }
}
