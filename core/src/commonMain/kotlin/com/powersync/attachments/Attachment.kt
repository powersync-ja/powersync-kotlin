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
    /**
     * Unique identifier
     */
    val id: String,
    /**
     * Timestamp for the last record update
     */
    val timestamp: Long = 0,
    /**
     * Attachment filename e.g. `[id].jpg`
     */
    val filename: String,
    /**
     * Current attachment state
     */
    val state: Int = AttachmentState.QUEUED_DOWNLOAD.ordinal,
    /**
     * Local URI pointing to the attachment file
     */
    val localUri: String? = null,
    /**
     * Attachment media type. Usually represented by a MIME type.
     */
    val mediaType: String? = null,
    /**
     * Attachment byte size
     */
    val size: Long? = null,
    /**
     * Specifies if the attachment has been synced locally before. This is particularly useful
     * for restoring archived attachments in edge cases.
     */
    val hasSynced: Int = 0,
    /**
     * Extra attachment meta data.
     */
    val metaData: String? = null,
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
                metaData = cursor.getStringOptional("meta_data"),
            )
    }
}
