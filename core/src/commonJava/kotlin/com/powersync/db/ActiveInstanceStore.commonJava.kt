package com.powersync.db

import java.lang.ref.Cleaner

internal actual fun disposeWhenDeallocated(resource: ActiveDatabaseResource): Any {
    // Note: It's important that the returned object does not reference the resource directly
    val wrapper = CleanableWrapper()
    CleanableWrapper.cleaner.register(wrapper, resource::dispose)
    return wrapper
}

private class CleanableWrapper {
    var cleanable: Cleaner.Cleanable? = null

    companion object {
        val cleaner: Cleaner = Cleaner.create()
    }
}
