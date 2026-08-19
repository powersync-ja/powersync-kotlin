package com.powersync.sync

import com.powersync.ExperimentalCheckpointRequestsApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException

@ExperimentalCheckpointRequestsApi
@OptIn(ExperimentalAtomicApi::class)
internal class CheckpointStateSignals {
    private val state = MutableStateFlow<CheckpointState>(CheckpointState.Pending)
    private val waitingForCheckpointsReady = AtomicReference(createNotifyWaitChannel())

    /**
     * Marks the current download iteration as ended, blocking new checkpoint requests until the
     * seed was performed in the next iteration.
     */
    fun downloadIterationEnded() {
        state.value = CheckpointState.Pending

        // Checkpoint waiters called after this should be able to resume the download iteration.
        val old = waitingForCheckpointsReady.exchange(createNotifyWaitChannel())
        old.cancel()
    }

    /**
     * Marks the sync client as disconnected, failing all outstanding checkpoint requests and
     * preventing new ones.
     */
    fun disconnected() {
        state.value = CheckpointState.Disconnected
    }

    /**
     * Waits for a waiter wanting to request a checkpoint.
     *
     * As the waiter is blocked for a seed run we start in the download iteration, we use this to
     * wake up the download iteration if it's currently paused.
     */
    suspend fun waitForCheckpointWaiter() {
        // Ignore errors if the channel is closed.
        waitingForCheckpointsReady.load().receiveCatching()
    }

    suspend fun markCheckpointsReady(block: suspend () -> Unit) {
        try {
            block()
            state.value = CheckpointState.DidSeed(Result.success(Unit))
        } catch (e: Exception) {
            if (e !is CancellationException) {
                state.value = CheckpointState.DidSeed(Result.failure(e))
            }

            throw e
        }
    }

    /**
     * Waits until a download iteration is active and has seeded the checkpoint state, meaning that
     * checkpoint ids can safely be allocated.
     */
    suspend fun waitForCheckpointRequestsReady(wakeDownloadLoop: Boolean = true) {
        state.first { state ->
            when (state) {
                is CheckpointState.DidSeed -> {
                    state.result.getOrThrow()
                    true
                }

                CheckpointState.Disconnected -> {
                    throw CheckpointRequestException.Disconnected()
                }

                CheckpointState.Pending -> {
                    if (wakeDownloadLoop) {
                        waitingForCheckpointsReady.load().trySend(Unit)
                    }
                    false
                }
            }
        }
    }

    companion object {
        private fun createNotifyWaitChannel() =
            Channel<Unit>(
                capacity = 1,
                onBufferOverflow = BufferOverflow.DROP_LATEST,
            )
    }
}

private sealed interface CheckpointState {
    object Pending : CheckpointState

    object Disconnected : CheckpointState

    class DidSeed(
        val result: Result<Unit>,
    ) : CheckpointState
}
