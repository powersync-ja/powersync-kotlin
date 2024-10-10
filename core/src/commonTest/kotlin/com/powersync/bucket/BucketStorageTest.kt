import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.test.*
import com.powersync.bucket.BucketStorage
import co.touchlab.kermit.Logger
import com.persistence.GetCrudFirstEntry
import com.powersync.bucket.BucketState
import com.powersync.db.internal.InternalDatabase
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

class BucketStorageTest {
    private lateinit var bucketStorage: BucketStorage
    private lateinit var mockDb: InternalDatabase

    @Test
    fun testGetMaxOpId() {
        mockDb = mock<InternalDatabase>() {
            every { getExistingTableNames("ps_data_*") } returns  listOf("list_1", "list_2")
        }
        bucketStorage = BucketStorage(mockDb, Logger)
        assertEquals("9223372036854775807", bucketStorage.getMaxOpId())
    }

    @Test
    fun testGetClientId() = runTest {
        mockDb = mock<InternalDatabase>() {
            every { getExistingTableNames("ps_data_*") } returns  listOf("list_1", "list_2")
            everySuspend { getOptional<String>(
                any(),
                any(),
                any()
            )} returns "test-client-id"
        }
        bucketStorage = BucketStorage(mockDb, Logger)
        val clientId = bucketStorage.getClientId()
        assertEquals("test-client-id", clientId)
    }

    @Test
    fun testGetClientIdThrowsException() = runTest {
        mockDb = mock<InternalDatabase>() {
            every { getExistingTableNames("ps_data_*") } returns  listOf("list_1", "list_2")
            everySuspend { getOptional<String>(
                any(),
                any(),
                any()
            )} returns null
        }
        bucketStorage = BucketStorage(mockDb, Logger)

        assertFailsWith<IllegalStateException> {
            bucketStorage.getClientId()
        }
    }

    @Test
    fun testNextCrudItem() = runTest {
        mockDb = mock<InternalDatabase>() {
            val mockFirstCrudEntry = GetCrudFirstEntry(1, 1, "test-data")
            everySuspend { queries.getCrudFirstEntry().awaitAsOneOrNull() } returns mockFirstCrudEntry
        }
        bucketStorage = BucketStorage(mockDb, Logger)


        val result = bucketStorage.nextCrudItem()
        assertNotNull(result)
    }

    @Test
    fun testHasCrud() = runTest {
        mockDb = mock<InternalDatabase>() {
            everySuspend { queries.hasCrud().awaitAsOneOrNull() } returns 1L
        }
        bucketStorage = BucketStorage(mockDb, Logger)

        assertTrue(bucketStorage.hasCrud())
    }

    @Test
    fun testUpdateLocalTarget() = runBlocking {
        mockDb = mock<InternalDatabase>() {
            every { getExistingTableNames("ps_data_*") } returns  listOf("list_1", "list_2")
            everySuspend { getOptional<Long>(
                any(),
                any(),
                any()
            )} returns 1L
            everySuspend { writeTransaction<Boolean>(any()) } returns true
        }
        bucketStorage = BucketStorage(mockDb, Logger)

        val result = bucketStorage.updateLocalTarget { "new-checkpoint" }
        assertTrue(result)
    }

//    @Test
//    fun testSaveSyncData() = runTest {
//        val mockSyncDataBatch = mock<SyncDataBatch>()
//        every { mockSyncDataBatch.buckets } returns listOf()
//        every { mockDb.writeTransaction(any()) } returns Unit
//
//        bucketStorage.saveSyncData(mockSyncDataBatch)
//        // Assert that the transaction was called (you might need to use a spy or different mocking strategy to verify this)
//    }

    @Test
    fun testGetBucketStates() = runTest {
        val mockBucketStates = listOf(BucketState("bucket1", "op1"), BucketState("bucket2", "op2"))
        mockDb = mock<InternalDatabase>() {
            every { getExistingTableNames("ps_data_*") } returns  listOf("list_1", "list_2")
            everySuspend { getOptional<Long>(
                any(),
                any(),
                any()
            )} returns 1L
            everySuspend { getAll<BucketState>(any(), any(), any()) } returns mockBucketStates
        }
        bucketStorage = BucketStorage(mockDb, Logger)

        val result = bucketStorage.getBucketStates()
        assertEquals(mockBucketStates, result)
    }

    @Test
    fun testRemoveBuckets() = runBlocking {
        mockDb = mock<InternalDatabase>() {
            every { getExistingTableNames("ps_data_*") } returns  listOf("list_1", "list_2")
            everySuspend { getOptional<Long>(
                any(),
                any(),
                any()
            )} returns 1L
            everySuspend { writeTransaction<Unit>(any()) } returns Unit
        }

        bucketStorage.removeBuckets(listOf("bucket1", "bucket2"))
        // Assert that the transaction was called for each bucket (you might need to use a spy or different mocking strategy to verify this)
    }

    @Test
    fun testHasCompletedSync() = runBlocking {
        every { mockDb.getOptional<String>(any(), null, any()) } returns "2023-01-01"

        assertTrue(bucketStorage.hasCompletedSync())
    }
}
