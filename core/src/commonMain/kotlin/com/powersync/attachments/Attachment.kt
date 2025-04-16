package com.powersync.attachments

import com.powersync.db.SqlCursor
import com.powersync.db.getLong
import com.powersync.db.getLongOptional
import com.powersync.db.getString
import com.powersync.db.getStringOptional

/**
 * Represents the state of an attachment.
 */
public enum class AttachmentState {
    /**
     * The attachment is queued for download from the remote storage.
     */
    QUEUED_DOWNLOAD,

    /**
     * The attachment is queued for upload to the remote storage.
     */
    QUEUED_UPLOAD,

    /**
     * The attachment is queued for deletion from the remote storage.
     */
    QUEUED_DELETE,

    /**
     * The attachment is fully synchronized with the remote storage.
     */
    SYNCED,

    /**
     * The attachment is archived and no longer actively synchronized.
     */
    ARCHIVED;

    public companion object {
        /**
         * Constructs an [AttachmentState] from the corresponding integer value.
         *
         * @param value The integer value representing the ordinal of the enum.
         * @return The corresponding [AttachmentState].
         * @throws IllegalArgumentException If the value does not match any [AttachmentState].
         */
        public fun fromLong(value: Long): AttachmentState {
            return entries.getOrNull(value.toInt())
                ?: throw IllegalArgumentException("Invalid value for AttachmentState: $value")
        }
    }
}

/**
 * Represents an attachment with metadata and state information.
 *
 * @property id Unique identifier for the attachment.
 * @property timestamp Timestamp of the last record update.
 * @property filename Name of the attachment file, e.g., `[id].jpg`.
 * @property state Current state of the attachment, represented as an ordinal of [AttachmentState].
 * @property localUri Local URI pointing to the attachment file, if available.
 * @property mediaType Media type of the attachment, typically represented as a MIME type.
 * @property size Size of the attachment in bytes, if available.
 * @property hasSynced Indicates whether the attachment has been synced locally before.
 * @property metaData Additional metadata associated with the attachment.
 */
public data class Attachment(
    val id: String,
    val timestamp: Long = 0,
    val filename: String,
    val state: AttachmentState = AttachmentState.QUEUED_DOWNLOAD,
    val localUri: String? = null,
    val mediaType: String? = null,
    val size: Long? = null,
    val hasSynced: Int = 0,
    val metaData: String? = null,
) {
    public companion object {
        /**
         * Creates an [Attachment] instance from a database cursor.
         *
         * @param cursor The [SqlCursor] containing attachment data.
         * @return An [Attachment] instance populated with data from the cursor.
         */
        public fun fromCursor(cursor: SqlCursor): Attachment =
            Attachment(
                id = cursor.getString(name = "id"),
                timestamp = cursor.getLong("timestamp"),
                filename = cursor.getString(name = "filename"),
                localUri = cursor.getStringOptional(name = "local_uri"),
                mediaType = cursor.getStringOptional(name = "media_type"),
                size = cursor.getLongOptional("size"),
                state = AttachmentState.fromLong(cursor.getLong("state")),
                hasSynced = cursor.getLong("has_synced").toInt(),
                metaData = cursor.getStringOptional("meta_data"),
            )
    }
}