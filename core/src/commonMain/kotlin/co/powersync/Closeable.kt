package co.powersync

/**
 * A type that can be closed.
 */
interface Closeable {
    /**
     * Whether this object has been closed.
     */
    var closed: Boolean

    /**
     * Close any resources backed by this object.
     */
    suspend fun close()
}
