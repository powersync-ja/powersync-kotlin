package com.powersync.sync

internal expect fun userAgent(): String

internal expect fun defaultClientImplementationSupportsBackpressure(): Boolean
