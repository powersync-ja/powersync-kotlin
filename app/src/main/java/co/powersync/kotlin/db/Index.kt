package co.powersync.kotlin.db

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

@Serializable
class Index(
    val name: String,
    @EncodeDefault val columns: Array<IndexedColumn> = arrayOf<IndexedColumn>()
)