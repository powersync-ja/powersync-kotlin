package co.powersync.bucket

data class BucketState(
    val bucket: String,
    val opId: String
) {
    override fun toString() = "BucketState<$bucket:$opId>"
}

