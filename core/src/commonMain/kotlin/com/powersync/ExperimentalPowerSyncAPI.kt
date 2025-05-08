package com.powersync

@RequiresOptIn(message = "This API is experimental and not covered by PowerSync semver releases. It can be changed at any time")
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
)
public annotation class ExperimentalPowerSyncAPI
