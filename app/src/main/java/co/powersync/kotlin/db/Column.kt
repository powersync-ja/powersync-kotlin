package co.powersync.kotlin.db

import kotlinx.serialization.Serializable

@Serializable
enum class ColumnType {
    TEXT,
    INTEGER,
    REAL
}

@Serializable
class Column (
    val name: String,
    val type: ColumnType?
)