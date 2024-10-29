import co.touchlab.kermit.Logger
import com.powersync.bucket.BucketState
import com.powersync.bucket.BucketStorageImpl
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.UpdateType
import com.powersync.db.internal.InternalDatabase
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BucketStorageTest {
    private lateinit var bucketStorage: BucketStorageImpl
    private lateinit var mockDb: InternalDatabase

    @Test
    fun testGetMaxOpId() {
        mockDb =
            mock<InternalDatabase> {
                every { getExistingTableNames("ps_data_*") } returns listOf("list_1", "list_2")
            }
        bucketStorage = BucketStorageImpl(mockDb, Logger)
        assertEquals("9223372036854775807", bucketStorage.getMaxOpId())
    }

    @Test
    fun testGetClientId() =
        runTest {
            mockDb =
                mock<InternalDatabase> {
                    every { getExistingTableNames("ps_data_*") } returns listOf("list_1", "list_2")
                    everySuspend {
                        getOptional<String>(
                            any(),
                            any(),
                            any(),
                        )
                    } returns "test-client-id"
                }
            bucketStorage = BucketStorageImpl(mockDb, Logger)
            val clientId = bucketStorage.getClientId()
            assertEquals("test-client-id", clientId)
        }

    @Test
    fun testGetClientIdThrowsException() =
        runTest {
            mockDb =
                mock<InternalDatabase> {
                    every { getExistingTableNames("ps_data_*") } returns listOf("list_1", "list_2")
                    everySuspend {
                        getOptional<String>(
                            any(),
                            any(),
                            any(),
                        )
                    } returns null
                }
            bucketStorage = BucketStorageImpl(mockDb, Logger)

            assertFailsWith<IllegalStateException> {
                bucketStorage.getClientId()
            }
        }

    @Test
    fun testNextCrudItem() =
        runTest {
            val mockCrudEntry =
                CrudEntry(
                    id = "1",
                    clientId = 1,
                    op = UpdateType.PUT,
                    table = "table1",
                    transactionId = 1,
                    opData =
                        mapOf(
                            "key" to "value",
                        ),
                )
            mockDb =
                mock<InternalDatabase> {
                    every { getExistingTableNames("ps_data_*") } returns listOf("list_1", "list_2")
                    everySuspend { getOptional<CrudEntry>(any(), any(), any()) } returns mockCrudEntry
                }
            bucketStorage = BucketStorageImpl(mockDb, Logger)

            val result = bucketStorage.nextCrudItem()
            assertEquals(mockCrudEntry, result)
        }

    @Test
    fun testNullNextCrudItem() =
        runTest {
            mockDb =
                mock<InternalDatabase> {
                    every { getExistingTableNames("ps_data_*") } returns listOf("list_1", "list_2")
                    everySuspend { getOptional<CrudEntry>(any(), any(), any()) } returns null
                }
            bucketStorage = BucketStorageImpl(mockDb, Logger)

            val result = bucketStorage.nextCrudItem()
            assertEquals(null, result)
        }

    @Test
    fun testHasCrud() =
        runTest {
            mockDb =
                mock<InternalDatabase> {
                    every { getExistingTableNames("ps_data_*") } returns listOf("list_1", "list_2")
                    everySuspend { getOptional<Long>(any(), any(), any()) } returns 1L
                }
            bucketStorage = BucketStorageImpl(mockDb, Logger)

            assertTrue(bucketStorage.hasCrud())
        }

    @Test
    fun testNullHasCrud() =
        runTest {
            mockDb =
                mock<InternalDatabase> {
                    every { getExistingTableNames("ps_data_*") } returns listOf("list_1", "list_2")
                    everySuspend { getOptional<CrudEntry>(any(), any(), any()) } returns null
                }
            bucketStorage = BucketStorageImpl(mockDb, Logger)

            assertFalse(bucketStorage.hasCrud())
        }

    @Test
    fun testUpdateLocalTarget() =
        runBlocking {
            mockDb =
                mock<InternalDatabase> {
                    every { getExistingTableNames("ps_data_*") } returns listOf("list_1", "list_2")
                    everySuspend {
                        getOptional<Long>(
                            any(),
                            any(),
                            any(),
                        )
                    } returns 1L
                    everySuspend { writeTransaction<Boolean>(any()) } returns true
                }
            bucketStorage = BucketStorageImpl(mockDb, Logger)

            val result = bucketStorage.updateLocalTarget { "new-checkpoint" }
            assertTrue(result)
        }

    @Test
    fun testGetBucketStates() =
        runTest {
            val mockBucketStates = listOf(BucketState("bucket1", "op1"), BucketState("bucket2", "op2"))
            mockDb =
                mock<InternalDatabase> {
                    every { getExistingTableNames("ps_data_*") } returns listOf("list_1", "list_2")
                    everySuspend {
                        getOptional<Long>(
                            any(),
                            any(),
                            any(),
                        )
                    } returns 1L
                    everySuspend { getAll<BucketState>(any(), any(), any()) } returns mockBucketStates
                }
            bucketStorage = BucketStorageImpl(mockDb, Logger)

            val result = bucketStorage.getBucketStates()
            assertEquals(mockBucketStates, result)
        }

    // TODO: Add tests for removeBuckets, hasCompletedSync, syncLocalDatabase currently not covered because
    //       currently the internal methods are private and cannot be accessed from the test class
}
