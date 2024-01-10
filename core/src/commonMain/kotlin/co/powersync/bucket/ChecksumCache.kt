package co.powersync.bucket

import co.powersync.bucket.BucketChecksum

data class ChecksumCache(val lostOpId: String, val checksums: Map<String, BucketChecksum>) {
}