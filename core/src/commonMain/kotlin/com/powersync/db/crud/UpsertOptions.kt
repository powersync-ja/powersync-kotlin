package com.powersync.db.crud

/**
 * Options for configuring upsert behavior when handling conflicts.
 * 
 * @property onConflict Comma-separated column name(s) to specify how duplicate rows are determined.
 *                      Two rows are duplicates if all the onConflict columns are equal.
 *                      If null, the primary key is used.
 * @property ignoreDuplicates If true, duplicate rows are ignored. If false, duplicate rows are merged with existing rows.
 */
public data class UpsertOptions(
    val onConflict: String? = null,
    val ignoreDuplicates: Boolean = false
) {
    public companion object {
        /**
         * Default upsert options that merge duplicates based on primary key.
         */
        public val DEFAULT: UpsertOptions = UpsertOptions()
        
        /**
         * Create upsert options from a list of conflict columns.
         */
        public fun fromColumns(columns: List<String>, ignoreDuplicates: Boolean = false): UpsertOptions =
            UpsertOptions(
                onConflict = columns.joinToString(","),
                ignoreDuplicates = ignoreDuplicates
            )
    }
}