package com.powersync.db.schema

internal val invalidSqliteCharacters = Regex("""["'%,.#\s\[\]]""")