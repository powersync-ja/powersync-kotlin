package co.powersync.sqlite.grammar.mixins

import app.cash.sqldelight.dialect.api.ExposableType
import app.cash.sqldelight.dialect.api.IntermediateType
import app.cash.sqldelight.dialect.api.PrimitiveType
import co.powersync.sqlite.grammar.PowersyncParser
import com.alecstrong.sql.psi.core.psi.SqlNamedElementImpl
import com.alecstrong.sql.psi.core.psi.SqlTableName
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
