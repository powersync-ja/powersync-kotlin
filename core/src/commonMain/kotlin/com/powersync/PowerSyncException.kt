package com.powersync

public class PowerSyncException(
    message: String,
    cause: Throwable,
) : Exception(message, cause)


/**
 * Helper class designed to bridge SKIEE methods and allow them to throw
 * `PowerSyncException`. This is necessary because these exceptions cannot
 * be thrown directly in Swift.
 *
 * The class provides a mechanism to handle exceptions in a way that is
 * compatible with the Swift environment, ensuring proper error propagation
 * and handling.
 */
@Throws(PowerSyncException::class)
public fun throwPowerSyncException(exception: PowerSyncException): Unit = throw exception
