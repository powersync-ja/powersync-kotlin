package com.powersync

import com.powersync.db.ThrowableLockCallback
import com.powersync.db.ThrowableTransactionCallback
import com.powersync.db.internal.ConnectionContext
import com.powersync.db.internal.PowerSyncTransaction

/**
 * The Kotlin [Result] type is an inline class which cannot be used on the Swift side.
 * This declares something similar which will help to bridge exceptions to Kotlin.
 * SKIEE doesn't handle generics well.
 * Making the Result type generic will cause the return type to be casted to "Any", but
 * also add restrictions that the generic should extend an object - which causes issues when returning
 * primitive types like `Int` or `String`.
 */
public sealed class PowerSyncResult {
    public data class Success(
        val value: Any,
    ) : PowerSyncResult()

    public data class Failure(
        val exception: PowerSyncException,
    ) : PowerSyncResult()
}

// Throws the [PowerSyncException] if the result is a failure, or returns the value if it is a success.
// We throw the exception on behalf of the Swift SDK.
@Throws(PowerSyncException::class)
private fun handleLockResult(result: PowerSyncResult): Any {
    when (result) {
        is PowerSyncResult.Failure -> {
            throw result.exception
        }

        is PowerSyncResult.Success -> {
            return result.value
        }
    }
}

public class LockContextWrapper(
    private val handler: (context: ConnectionContext) -> PowerSyncResult,
) : ThrowableLockCallback<Any> {
    override fun execute(context: ConnectionContext): Any = handleLockResult(handler(context))
}

public class TransactionContextWrapper(
    private val handler: (context: PowerSyncTransaction) -> PowerSyncResult,
) : ThrowableTransactionCallback<Any> {
    override fun execute(transaction: PowerSyncTransaction): Any = handleLockResult(handler(transaction))
}

public fun wrapContextHandler(handler: (context: ConnectionContext) -> PowerSyncResult): ThrowableLockCallback<Any> =
    LockContextWrapper(handler)

public fun wrapTransactionContextHandler(handler: (context: PowerSyncTransaction) -> PowerSyncResult): ThrowableTransactionCallback<Any> =
    TransactionContextWrapper(handler)
