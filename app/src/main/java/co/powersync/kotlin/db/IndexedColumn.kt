package co.powersync.kotlin.db

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

@Serializable
class IndexedColumn(
    val name: String,
    @EncodeDefault val ascending: Boolean = true,
    @EncodeDefault val type: ColumnType = ColumnType.TEXT
)