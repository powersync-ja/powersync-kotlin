package com.powersync.db.internal

internal data class PowerSyncVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<PowerSyncVersion> {
    override fun compareTo(other: PowerSyncVersion): Int =
        when (val compareMajor = major.compareTo(other.major)) {
            0 ->
                when (val compareMinor = minor.compareTo(other.minor)) {
                    0 -> patch.compareTo(other.patch)
                    else -> compareMinor
                }
            else -> compareMajor
        }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        val MINIMUM: PowerSyncVersion = PowerSyncVersion(0, 3, 14)

        fun parse(from: String): PowerSyncVersion {
            val versionInts: List<Int> =
                try {
                    from
                        .split(Regex("[./]"))
                        .take(3)
                        .map { it.toInt() }
                } catch (e: Exception) {
                    mismatchError(from, e.toString())
                }

            return PowerSyncVersion(versionInts[0], versionInts[1], versionInts[2])
        }

        fun mismatchError(
            actualVersion: String,
            details: String? = null,
        ): Nothing {
            var message = "Unsupported PowerSync extension version (need ^$MINIMUM, got $actualVersion)."
            if (details != null) {
                message = " Details: $details"
            }

            throw Exception(message)
        }
    }
}
