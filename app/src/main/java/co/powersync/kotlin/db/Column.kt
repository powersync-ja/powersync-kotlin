package co.powersync.kotlin.db

enum class ColumnType {
    TEXT,
    INTEGER,
    REAL
}

class ColumnOptions(
    val name: String,
    val type: ColumnType?
)

class Column (
    val options: ColumnOptions
) {

    val name
        get () = options.name;

    val type
        get () = options.type;

    fun toJSON() {
        TODO("Implement")
        // Maybe we can get away with using the @Serializable and data modifiers
    }
}