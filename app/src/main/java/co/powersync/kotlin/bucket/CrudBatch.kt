package co.powersync.kotlin.bucket

class CrudBatch (
    val crud: Array<CrudEntry>,
    val haveMore: Boolean,
    val complete: (writeCheckpoint: Boolean?) -> Unit
)