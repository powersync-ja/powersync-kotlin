package powersync.sync

import app.cash.turbine.turbineScope
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.bucket.BucketStorage
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.UpdateType
import com.powersync.db.schema.Schema
import com.powersync.sync.StreamingSyncClient
import com.powersync.sync.StreamingSyncClient.Companion.bsonObjects
import com.powersync.sync.SyncClientConfiguration
import com.powersync.sync.SyncOptions
import com.powersync.sync.SyncStatus
import com.powersync.sync.configureSyncHttpClient
import com.powersync.test.TestConnector
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeByteArray
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.EOFException
import kotlinx.serialization.json.JsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalKermitApi::class, ExperimentalPowerSyncAPI::class)
class StreamingSyncClientTest {
    private lateinit var status: SyncStatus
    private lateinit var bucketStorage: BucketStorage
    private lateinit var connector: PowerSyncBackendConnector
    private lateinit var streamingSyncClient: StreamingSyncClient
    private val testLogWriter =
        TestLogWriter(
            loggable = Severity.Verbose,
        )
    private val logger =
        Logger(
            TestConfig(
                minSeverity = Severity.Debug,
                logWriterList = listOf(testLogWriter),
            ),
        )
    private val assertNoHttpEngine =
        MockEngine { request ->
            error("Unexpected HTTP request: $request")
        }

    @BeforeTest
    fun setup() {
        status = SyncStatus()
        bucketStorage =
            mock<BucketStorage> {
                everySuspend { getClientId() } returns "test-client-id"
            }
        connector = TestConnector()
    }

    @Test
    fun testInvalidateCredentials() =
        runTest {
            streamingSyncClient =
                StreamingSyncClient(
                    status = status,
                    bucketStorage = bucketStorage,
                    connector = connector,
                    uploadCrud = {},
                    logger = logger,
                    params = JsonObject(emptyMap()),
                    options =
                        SyncOptions(
                            clientConfiguration =
                                SyncClientConfiguration.ExistingClient(
                                    HttpClient(assertNoHttpEngine) {
                                        configureSyncHttpClient()
                                    },
                                ),
                        ),
                    schema = Schema(),
                    activeSubscriptions = MutableStateFlow(emptyList()),
                )

            connector.cachedCredentials = TestConnector.testCredentials
            streamingSyncClient.invalidateCredentials()
            connector.cachedCredentials shouldBe null
        }

    // TODO: Work on improving testing this without needing to test the logs are displayed
    @Test
    fun testTriggerCrudUploadWhenAlreadyUploading() =
        runTest {
            val mockCrudEntry =
                CrudEntry(
                    id = "1",
                    clientId = 1,
                    op = UpdateType.PUT,
                    table = "table1",
                    transactionId = 1,
                    opData = null,
                )
            bucketStorage =
                mock<BucketStorage> {
                    everySuspend { getClientId() } returns "test-id"
                    everySuspend { nextCrudItem() } returns mockCrudEntry
                }

            streamingSyncClient =
                StreamingSyncClient(
                    status = status,
                    bucketStorage = bucketStorage,
                    connector = connector,
                    uploadCrud = { },
                    retryDelay = 10.seconds,
                    logger = logger,
                    params = JsonObject(emptyMap()),
                    options =
                        SyncOptions(
                            clientConfiguration =
                                SyncClientConfiguration.ExistingClient(
                                    HttpClient(assertNoHttpEngine) {
                                        configureSyncHttpClient()
                                    },
                                ),
                        ),
                    schema = Schema(),
                    activeSubscriptions = MutableStateFlow(emptyList()),
                )

            val sync = launch { streamingSyncClient.streamingSync() }
            delay(2.seconds)
            sync.cancelAndJoin()

            val logs = testLogWriter.logs.filter { it.message.contains("CRUD") }

            with(logs[0]) {
                assertContains(
                    message,
                    "Potentially previously uploaded CRUD entries are still present in the upload queue.",
                )
                assertEquals(Severity.Warn, severity)
            }

            with(logs[1]) {
                assertEquals(
                    message,
                    "Error uploading crud: Delaying due to previously encountered CRUD item.",
                )
                assertEquals(Severity.Error, severity)
            }
        }

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
