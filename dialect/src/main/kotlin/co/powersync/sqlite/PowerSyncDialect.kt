package co.powersync.sqlite

import app.cash.sqldelight.dialect.api.IntermediateType
import app.cash.sqldelight.dialect.api.PrimitiveType
import app.cash.sqldelight.dialect.api.SqlDelightDialect
import app.cash.sqldelight.dialect.api.TypeResolver
import app.cash.sqldelight.dialects.sqlite_3_35.SqliteTypeResolver
import app.cash.sqldelight.dialects.sqlite_3_38.SqliteDialect
import com.alecstrong.sql.psi.core.psi.SqlFunctionExpr

class PowerSyncDialect : SqlDelightDialect by SqliteDialect() {
    override fun typeResolver(parentResolver: TypeResolver): TypeResolver =
        PowerSyncTypeResolver(parentResolver)
}

private class PowerSyncTypeResolver(private val parentResolver: TypeResolver) :
    TypeResolver by SqliteTypeResolver(parentResolver) {
    override fun functionType(functionExpr: SqlFunctionExpr): IntermediateType? {
        when (functionExpr.functionName.text) {
            "sqlite_version", "powersync_rs_version", "powersync_replace_schema" -> return IntermediateType(
                PrimitiveType.TEXT
            )
        }
        return parentResolver.functionType(functionExpr)
    }


}
