package powersync.sync

import app.cash.turbine.turbineScope
import com.powersync.sync.StreamingSyncClient.Companion.bsonObjects
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.io.EOFException
import kotlin.test.Test

class StreamingSyncClientTest {
    @Test
    fun splitBsonObjects() =
        runTest {
            turbineScope {
                val channel = ByteChannel()
                val objects = channel.bsonObjects().testIn(this)

                channel.writeByteArray(byteArrayOf(5, 0, 0, 0, 1))
                channel.flush()
                objects.awaitItem() shouldHaveSize 5

                channel.writeByteArray(byteArrayOf(6, 0))
                channel.flush()
                channel.writeByteArray(byteArrayOf(0, 0))
                channel.flush()
                channel.writeByteArray(byteArrayOf(0, 0))
                channel.flush()
                objects.awaitItem() shouldHaveSize 6

                channel.close()
                objects.awaitComplete()
            }
        }

    @Test
    fun invalidBsonSize() =
        runTest {
            turbineScope {
                val channel = ByteChannel()
                val objects = channel.bsonObjects().testIn(this)

                channel.writeByteArray(byteArrayOf(3, 0, 0, 0))
                channel.flush()

                objects.awaitError()
                objects.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun invalidEndInLength() =
        runTest {
            turbineScope {
                val channel = ByteChannel()
                val objects = channel.bsonObjects().testIn(this)

                channel.writeByteArray(byteArrayOf(5, 0))
                channel.flush()
                channel.close()

                // Still two bytes missing for length
                objects.awaitError().shouldBeTypeOf<EOFException>()
            }
        }

    @Test
    fun invalidEndInObject() =
        runTest {
            turbineScope {
                val channel = ByteChannel()
                val objects = channel.bsonObjects().testIn(this)

                channel.writeByteArray(byteArrayOf(6, 0, 0, 0))
                channel.flush()
                channel.close()

                // Still two bytes missing for content
                objects.awaitError().shouldBeTypeOf<EOFException>()
            }
        }
}
