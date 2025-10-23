@file:Suppress("ktlint:standard:filename")

package com.powersync.db.schema

internal val invalidSqliteCharacters = Regex("""["'%,.#\s\[\]]""")
