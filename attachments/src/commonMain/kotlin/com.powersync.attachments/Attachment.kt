package com.powersync.attachments

import com.powersync.db.SqlCursor
import com.powersync.db.getLong
import com.powersync.db.getString

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
) {
    public companion object {
        public fun fromCursor(cursor: SqlCursor): Attachment =
            Attachment(
                id = cursor.getString(name = "id"),
                timestamp = cursor.getLong("timestamp"),
                filename = cursor.getString(name = "filename"),
                localUri = cursor.getString(name = "local_uri"),
                mediaType = cursor.getString(name = "media_type"),
                size = cursor.getLong("size"),
                state = cursor.getLong("state").toInt(),
            )
    }
}
