package co.powersync.kotlin.db

class TableOptions (
    val name: String,
    val columns: Array<Column>,
    val indexes: Array<Index>?,
    val localOnly: Boolean?,
    val insertOnly: Boolean?,
)

class Table(
    val options: TableOptions
) {
    val name
        get () = options.name;

    val columns
        get () = options.columns;

    val indexes
        get () = options.indexes;

    val localOnly
        get () = options.localOnly;

    val insertOnly
        get () = options.insertOnly;

    fun toJSON() {
        TODO("Implement")
    }
}