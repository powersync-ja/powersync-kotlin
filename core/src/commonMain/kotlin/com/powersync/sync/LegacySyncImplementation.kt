package com.powersync.sync

@RequiresOptIn(message = "Marker class for the old Kotlin-based sync implementation, making it easier to recognize classes we can remove after switching to the Rust sync implementation.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
internal annotation class LegacySyncImplementation
