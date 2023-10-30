package co.powersync.kotlin.db

import kotlinx.serialization.Serializable

@Serializable
data class Schema(val tables:Array<Table>)