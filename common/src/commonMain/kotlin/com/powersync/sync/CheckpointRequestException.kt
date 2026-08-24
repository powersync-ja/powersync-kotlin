package com.powersync.sync

import com.powersync.ExperimentalCheckpointRequestsApi
import com.powersync.PowerSyncException

/**
 * An exception related to checkpoint requests.
 */
@ExperimentalCheckpointRequestsApi
public open class CheckpointRequestException internal constructor(
    message: String,
    cause: Throwable? = null,
) : PowerSyncException(message, cause) {
    public class InstanceNotSupported internal constructor() :
        CheckpointRequestException(
            "The PowerSync service does not support checkpoint requests. Update to PowerSync service version 1.24.0 or later to use this API.",
        )

    public class Disconnected internal constructor() : CheckpointRequestException("Cannot request checkpoints, sync client is disconnected")

    public class Disabled internal constructor() :
        CheckpointRequestException("Connected with legacy checkpoint mode, cannot request checkpoints")

    public class StatusError internal constructor(
        cause: Throwable?,
    ) : CheckpointRequestException("Error on sync status before checkpoint was applied", cause)
}
