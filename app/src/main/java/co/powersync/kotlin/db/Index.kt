package co.powersync.kotlin.db

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
class Index @OptIn(ExperimentalSerializationApi::class) constructor(
    val name: String,
    @EncodeDefault val columns: Array<IndexedColumn> = arrayOf<IndexedColumn>()
)