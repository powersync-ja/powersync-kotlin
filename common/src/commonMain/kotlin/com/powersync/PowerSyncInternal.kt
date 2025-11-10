package com.powersync

@RequiresOptIn(message = "This API should not be used outside of PowerSync SDK packages")
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
)
public annotation class PowerSyncInternal
