package com.powersync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Function interface for dispatching database operations to a specific coroutine context.
 *
 * By default, operations are dispatched to [Dispatchers.IO]. Custom implementations
 * can be provided to control the execution context of database operations.
 *
 * This interface supports the `operator invoke` syntax, allowing you to call it like:
 * ```
 * dispatchFunction { /* your code */ }
 * ```
 *
 * **Design Note:** This must be an interface (not a function type) because Kotlin does not
 * support function types with generic type parameters. Since the dispatch function needs to
 * accept and return generic types `<R>`, an interface with an `operator invoke` method is
 * the appropriate solution. This allows the same convenient syntax as function types while
 * supporting generics.
 *
 * @see DispatchStrategy for dispatch strategy options
 */
public interface DispatchFunction {
    /**
     * Dispatches the given block to the appropriate coroutine context.
     *
     * @param block The suspend function to execute in the dispatch context.
     * @return The result of executing the block.
     */
    public suspend operator fun <R> invoke(block: suspend () -> R): R
}

/**
 * Strategy for dispatching database operations to a specific coroutine context.
 *
 * This sealed class allows you to specify how database operations should be dispatched:
 * - [Default]: Use the default dispatcher ([Dispatchers.IO])
 * - [Dispatcher]: Use a specific [CoroutineDispatcher]
 * - [Custom]: Use a custom [DispatchFunction] for full control
 *
 * Each variant provides a [dispatchFunction] that implements the actual dispatching logic.
 *
 * Example usage:
 * ```
 * // Use default (Dispatchers.IO) - this is the default if not specified
 * PowerSyncDatabase(factory, schema)
 * // or explicitly:
 * PowerSyncDatabase(factory, schema, dispatchStrategy = DispatchStrategy.Default)
 *
 * // Use a specific dispatcher
 * PowerSyncDatabase(factory, schema, dispatchStrategy = DispatchStrategy.Dispatcher(Dispatchers.Default))
 *
 * // Use a custom function
 * PowerSyncDatabase(factory, schema, dispatchStrategy = DispatchStrategy.Custom(myCustomFunction))
 * ```
 *
 * @see DispatchFunction for the dispatch function interface
 */
public sealed class DispatchStrategy {
    /**
     * Returns the [DispatchFunction] that implements the dispatching logic for this strategy.
     */
    public abstract val dispatchFunction: DispatchFunction

    /**
     * Use the default dispatcher ([Dispatchers.IO]) for database operations.
     *
     * This is the recommended default for most use cases, as it provides
     * a dedicated thread pool for I/O-bound operations.
     */
    public object Default : DispatchStrategy() {
        override val dispatchFunction: DispatchFunction =
            Dispatcher(Dispatchers.IO).dispatchFunction
    }

    /**
     * Use a specific [CoroutineDispatcher] for database operations.
     *
     * This allows you to use any coroutine dispatcher, such as:
     * - [Dispatchers.Default] for CPU-bound work
     * - [Dispatchers.Main] for UI operations
     * - A custom dispatcher for your specific needs
     *
     * @property dispatcher The coroutine dispatcher to use.
     */
    public data class Dispatcher(
        val dispatcher: CoroutineDispatcher,
    ) : DispatchStrategy() {
        override val dispatchFunction: DispatchFunction =
            object : DispatchFunction {
                override suspend fun <R> invoke(block: suspend () -> R): R = withContext(dispatcher) { block() }
            }
    }

    /**
     * Use a custom [DispatchFunction] for full control over dispatching.
     *
     * @property function The custom dispatch function to use.
     */
    public data class Custom(
        val function: DispatchFunction,
    ) : DispatchStrategy() {
        override val dispatchFunction: DispatchFunction = function
    }
}
