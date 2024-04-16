package com.powersync.db.crud

import kotlinx.serialization.Serializable

@Serializable
public data class CrudRow(val id: String, val data: String, val txId: Int?)