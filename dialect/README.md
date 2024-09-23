# SQLDelight Custom PowerSync Dialect

This defines the custom PowerSync SQLite functions to be used in the `PowerSync.sq` file found in the `persistence` module.

## Example
```kotlin
public class PowerSyncTypeResolver(private val parentResolver: TypeResolver) :
    TypeResolver by SqliteTypeResolver(parentResolver) {
    override fun functionType(functionExpr: SqlFunctionExpr): IntermediateType? {
        when (functionExpr.functionName.text) {
            "powersync_replace_schema" -> return IntermediateType(
                PrimitiveType.TEXT
            )
        }
        return parentResolver.functionType(functionExpr)
    }
}
```

allows

```sql
replaceSchema:
SELECT powersync_replace_schema(?);
```

To be used in the `PowerSync.sq` file in the `persistence` module.