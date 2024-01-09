package co.powersync.db.crud

/**
 * Type of local change.
 */
enum class UpdateType(val json: String) {
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

    fun toJson(): String {
        return json
    }

    companion object {
        fun fromJson(json: String): UpdateType? {
            return entries.find { it.json == json }
        }

        fun fromJsonChecked(json: String): UpdateType {
            val v = fromJson(json)
            requireNotNull(v) { "Unexpected updateType: $json" }
            return v
        }
    }
}