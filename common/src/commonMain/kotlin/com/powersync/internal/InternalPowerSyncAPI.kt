package com.powersync.internal

@RequiresOptIn(message = "This API is internal to PowerSync and should never be used outside of the SDK.")
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY,
)
public annotation class InternalPowerSyncAPI
