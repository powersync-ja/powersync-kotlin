package com.powersync.utils

internal object Strings {
    fun quoteIdentifier(s: String): String {
        return "\"${s.replace("\"", "\"\"")}\""
    }
}