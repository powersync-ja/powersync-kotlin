package co.powersync.kotlin.db

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
class IndexedColumn @OptIn(ExperimentalSerializationApi::class) constructor(
    val name: String,
    @EncodeDefault val ascending: Boolean = true,
    @EncodeDefault val type: ColumnType = ColumnType.TEXT
    // TODO the type needs to come from the actual listed column e.g.
    //   toJSON(table: Table) {
    //    return {
    //      ...
    //      type: table.columns.find((column) => column.name === this.name)?.type ?? ColumnType.TEXT
    //    };
    //  }
)