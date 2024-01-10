package co.powersync.sync

data class SyncLocalDatabaseResult(
    val ready: Boolean = true,
    val checkpointValid: Boolean = true,
    val checkpointFailures: List<String>? = null
) {
    override fun toString() = "SyncLocalDatabaseResult<ready=$ready, checkpointValid=$checkpointValid, failures=$checkpointFailures>"
}