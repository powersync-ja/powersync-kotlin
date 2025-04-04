package com.powersync.attachments

import com.powersync.db.SqlCursor
import com.powersync.db.getLong
import com.powersync.db.getLongOptional
import com.powersync.db.getString
import com.powersync.db.getStringOptional

/**
 * Enum for the attachment state
 */
public enum class AttachmentState {
    QUEUED_DOWNLOAD,
    QUEUED_UPLOAD,
    QUEUED_DELETE,
    SYNCED,
    ARCHIVED,
}

/**
 * Data class representing an attachment
 */
public data class Attachment(
    val id: String,
    val timestamp: Long = 0,
    val filename: String,
    val state: Int = AttachmentState.QUEUED_DOWNLOAD.ordinal,
    val localUri: String? = null,
    val mediaType: String? = null,
    val size: Long? = null,
    /**
     * Specifies if the attachment has been synced locally before. This is particularly useful
     * for restoring archived attachments in edge cases.
     */
    val hasSynced: Int = 0,
) {
    public companion object {
        public fun fromCursor(cursor: SqlCursor): Attachment =
            Attachment(
                id = cursor.getString(name = "id"),
                timestamp = cursor.getLong("timestamp"),
                filename = cursor.getString(name = "filename"),
                localUri = cursor.getStringOptional(name = "local_uri"),
                mediaType = cursor.getStringOptional(name = "media_type"),
                size = cursor.getLongOptional("size"),
                state = cursor.getLong("state").toInt(),
                hasSynced = cursor.getLong("has_synced").toInt(),
            )
    }
}
