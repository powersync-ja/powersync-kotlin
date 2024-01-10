package co.powersync.bucket

enum class OpType(val value: Int) {
    CLEAR(1),
    MOVE(2),
    PUT(3),
    REMOVE(4);

    companion object {
        fun fromJson(json: String): OpType? {
            return when (json) {
                "CLEAR" -> CLEAR
                "MOVE" -> MOVE
                "PUT" -> PUT
                "REMOVE" -> REMOVE
                else -> null
            }
        }
    }
}