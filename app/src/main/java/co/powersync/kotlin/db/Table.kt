package co.powersync.kotlin.db

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
class Table @OptIn(ExperimentalSerializationApi::class) constructor(
    val name: String,
    val columns: Array<Column>,
    @EncodeDefault val indexes: Array<Index> = arrayOf(),
    @EncodeDefault val localOnly: Boolean = false,
    @EncodeDefault val insertOnly: Boolean = false,
)