package com.powersync.db.crud

/**
 * Type of local change.
 */
public enum class UpdateType(private val json: String) {
    /**
     * Insert or replace a row. All non-null columns are included in the data.
     */
    PUT("PUT"),

    /**
     * Update a row if it exists. All updated columns are included in the data.
     */
    PATCH("PATCH"),

    /**
     * Delete a row if it exists.
     */
    DELETE("DELETE");

    public fun toJson(): String {
        return json
    }

    public companion object {
        public fun fromJson(json: String): UpdateType? {
            return entries.find { it.json == json }
        }

        public fun fromJsonChecked(json: String): UpdateType {
            val v = fromJson(json)
            requireNotNull(v) { "Unexpected updateType: $json" }
            return v
        }
    }
}