package com.powersync.web

import kotlin.test.Test

class RawWebSqliteTest {
    @Test
    fun canUseJavaScriptSymbols() {
        val x = DatabaseImplementation.opfsShared
        println(x.access)
    }
}
