package co.powersync.sqlite.grammar.mixins

import app.cash.sqldelight.dialect.api.ExposableType
import app.cash.sqldelight.dialect.api.IntermediateType
import app.cash.sqldelight.dialect.api.PrimitiveType
import co.powersync.sqlite.grammar.PowersyncParser
import co.powersync.sqlite.grammar.psi.SqlitePowersyncTableOrSubquery
import com.alecstrong.sql.psi.core.ModifiableFileLazy
import com.alecstrong.sql.psi.core.psi.QueryElement.QueryResult
import com.alecstrong.sql.psi.core.psi.QueryElement.SynthesizedColumn
import com.alecstrong.sql.psi.core.psi.SqlExpr
import com.alecstrong.sql.psi.core.psi.SqlJoinClause
import com.alecstrong.sql.psi.core.psi.SqlNamedElementImpl
import com.alecstrong.sql.psi.core.psi.SqlTableName
import com.alecstrong.sql.psi.core.psi.impl.SqlTableOrSubqueryImpl
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.psi.PsiElement

internal abstract class PowerSyncFunctionNameMixin(node: ASTNode) : SqlNamedElementImpl(node),
    SqlTableName, ExposableType {
    override fun getId(): PsiElement? = null
    override fun getString(): PsiElement? = null
    override val parseRule: (PsiBuilder, Int) -> Boolean = PowersyncParser::ps_function_name_real
    override fun type() = IntermediateType(PrimitiveType.TEXT)
}

internal abstract class TableOrSubqueryMixin(node: ASTNode?) : SqlTableOrSubqueryImpl(node),
    SqlitePowersyncTableOrSubquery {
    private val queryExposed = ModifiableFileLazy lazy@{
        if (psFunctionName != null) {
            return@lazy listOf(
                QueryResult(
                    table = psFunctionName!!,
                    columns = emptyList(),
                    synthesizedColumns = listOf(
                        SynthesizedColumn(
                            psFunctionName!!,
                            acceptableValues = listOf(
                                "key",
                                "value",
                                "type",
                                "atom",
                                "id",
                                "parent",
                                "fullkey",
                                "path",
                                "json",
                                "root"
                            )
                        ),
                    ),
                ),
            )
        }
        super.queryExposed()
    }

    override fun queryExposed() = queryExposed.forFile(containingFile)

    override fun queryAvailable(child: PsiElement): Collection<QueryResult> {
        if (child is SqlExpr) {
            val parent = parent as SqlJoinClause
            return parent.tableOrSubqueryList.takeWhile { it != this }.flatMap { it.queryExposed() }
        }
        return super.queryAvailable(child)
    }
}


