// This is required to build the iOS framework

package com.powersync

import com.powersync.sync.ConnectionMethod

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

/**
 * Creates a [ConnectionMethod] from a simple boolean, because creating the actual instance with
 * the default constructor is not possible from Swift due to an optional argument with an internal
 * default value.
 */
@OptIn(ExperimentalPowerSyncAPI::class)
public fun createConnectionMethod(webSocket: Boolean): ConnectionMethod =
    if (webSocket) {
        ConnectionMethod.WebSocket()
    } else {
        ConnectionMethod.Http
    }
