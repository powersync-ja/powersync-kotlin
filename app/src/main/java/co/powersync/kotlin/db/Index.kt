package co.powersync.kotlin.db

class IndexOptions (
    val name: String,
    val columns: Array<IndexedColumn> = arrayOf<IndexedColumn>()
)

class Index(val options: IndexOptions) {
    val name
        get () = options.name

    val columns
        get () = options.columns
}