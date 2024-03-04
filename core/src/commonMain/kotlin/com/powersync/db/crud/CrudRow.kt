package com.powersync.db.crud

import kotlinx.serialization.Serializable

@Serializable
data class CrudRow(val id: String, val data: String, val txId: Int?)