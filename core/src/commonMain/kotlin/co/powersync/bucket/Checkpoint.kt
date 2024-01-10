package co.powersync.bucket

data class Checkpoint(val lastOpId: String, val checksums: List<BucketChecksum>, val writeCheckpoint: String?) {

    fun clone(): Checkpoint {
        return Checkpoint(lastOpId, checksums, writeCheckpoint)
    }

//    companion object {
//        fun fromJson(json: Map<String, Any>): Checkpoint {
//            return Checkpoint(
//                lastOpId = json["last_op_id"] as String,
//                checksums = (json["checksums"] as List<Map<String, Any>>).map { BucketChecksum(it) },
//                writeCheckpoint = json["write_checkpoint"] as String?
//            )
//        }
//    }
}