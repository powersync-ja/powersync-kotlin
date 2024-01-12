package co.powersync.db.schema

import kotlinx.serialization.Serializable

@Serializable
data class Schema(val tables: List<Table>) {
}