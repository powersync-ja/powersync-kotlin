package co.powersync.kotlin.bucket

enum class UpdateType {
    PUT ,
    PATCH,
    DELETE
}
class CrudEntry (
    val clientId: Int,
    val op: UpdateType,
    val table: String,
    val id: String,
    val transactionId: Int?,
    val opData: Map<String, Any>?
)