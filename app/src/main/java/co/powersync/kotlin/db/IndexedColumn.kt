package co.powersync.kotlin.db

class IndexColumnOptions (
    val name: String,
    val ascending: Boolean = true
)

class IndexedColumn(val options: IndexColumnOptions) {
    val name
        get () = options.name;

    val ascending
        get () = options.ascending;
}