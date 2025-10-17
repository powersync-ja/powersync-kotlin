package com.powersync.sync

import app.cash.turbine.turbineScope
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.bucket.StreamPriority
import com.powersync.testutils.databaseTest
import com.powersync.testutils.waitFor
import com.powersync.utils.JsonParam
import com.powersync.utils.JsonUtil
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test

@OptIn(ExperimentalPowerSyncAPI::class, LegacySyncImplementation::class)
class SyncStreamTest : AbstractSyncTest(true) {
    @Test
    fun `can disable default streams`() =
        databaseTest {
            database.connect(
                connector,
                options =
                    SyncOptions(
                        newClientImplementation = true,
                        includeDefaultStreams = false,
                        clientConfiguration = SyncClientConfiguration.ExistingClient(createSyncClient()),
                    ),
            )

            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected && !it.downloading }

                requestedSyncStreams shouldHaveSingleElement {
                    val streams = it.jsonObject["streams"]!!.jsonObject
                    streams["include_defaults"]!!.jsonPrimitive.content shouldBe "false"

                    true
                }

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `subscribes with streams`() =
        databaseTest {
            val a = database.syncStream("stream", mapOf("foo" to JsonParam.String("a"))).subscribe()
            val b = database.syncStream("stream", mapOf("foo" to JsonParam.String("b"))).subscribe(priority = StreamPriority(1))

            database.connect(connector, options = getOptions())
            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected && !it.downloading }

                // Should request subscriptions
                requestedSyncStreams shouldHaveSingleElement {
                    val streams = it.jsonObject["streams"]!!.jsonObject
                    val subscriptions = streams["subscriptions"]!!.jsonArray

                    subscriptions shouldHaveSize 2
                    JsonUtil.json.encodeToString(subscriptions[0]) shouldBe
                        """{"stream":"stream","parameters":{"foo":"a"},"override_priority":null}"""
                    JsonUtil.json.encodeToString(subscriptions[1]) shouldBe
                        """{"stream":"stream","parameters":{"foo":"b"},"override_priority":1}"""
                    true
                }

                syncLines.send(
                    checkpointLine(
                        listOf(
                            bucket(
                                "a",
                                3,
                                subscriptions =
                                    buildJsonArray {
                                        add(defaultSubscription(0))
                                    },
                            ),
                            bucket(
                                "b",
                                1,
                                subscriptions =
                                    buildJsonArray {
                                        add(defaultSubscription(1))
                                    },
                            ),
                        ),
                        listOf(stream("stream", false)),
                    ),
                )

                // Subscriptions should be active now, but not marked as synced.
                var status = turbine.awaitItem()
                for (subscription in listOf(a, b)) {
                    val subscriptionStatus = status.forStream(subscription)!!
                    subscriptionStatus.subscription.active shouldBe true
                    subscriptionStatus.subscription.lastSyncedAt shouldBe null
                    subscriptionStatus.subscription.hasExplicitSubscription shouldBe true
                }

                syncLines.send(
                    SyncLine.CheckpointPartiallyComplete(
                        lastOpId = "0",
                        priority = StreamPriority(1),
                    ),
                )
                status = turbine.awaitItem()
                status.forStream(a)!!.subscription.lastSyncedAt shouldBe null
                status.forStream(b)!!.subscription.lastSyncedAt shouldNotBeNull {}
                b.waitForFirstSync()

                syncLines.send(SyncLine.CheckpointComplete(lastOpId = "0"))
                a.waitForFirstSync()

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reports default streams`() =
        databaseTest {
            database.connect(connector, options = getOptions())
            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected && !it.downloading }

                syncLines.send(checkpointLine(listOf(), listOf(stream("default_stream", true))))

                val status = turbine.awaitItem()
                status.syncStreams!! shouldHaveSingleElement {
                    it.subscription.name shouldBe "default_stream"
                    it.subscription.parameters shouldBe null
                    it.subscription.isDefault shouldBe true
                    it.subscription.hasExplicitSubscription shouldBe false
                    true
                }

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun `changes subscriptions dynamically`() =
        databaseTest {
            database.connect(connector, options = getOptions())
            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected && !it.downloading }
                requestedSyncStreams.clear()

                val subscription = database.syncStream("a").subscribe()
                waitForSyncLinesChannelClosed()

                // Adding the subscription should reconnect
                turbine.waitFor { it.connected }
                requestedSyncStreams shouldHaveSingleElement {
                    val streams = it.jsonObject["streams"]!!.jsonObject
                    val subscriptions = streams["subscriptions"]!!.jsonArray

                    subscriptions shouldHaveSize 1
                    JsonUtil.json.encodeToString(subscriptions[0]) shouldBe """{"stream":"a","parameters":null,"override_priority":null}"""
                    true
                }

                // Given that the subscription has a default TTL, unsubscribing should not re-subscribe.
                subscription.unsubscribe()
                delay(100)
                turbine.expectNoEvents()

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `subscriptions update while offline`() =
        databaseTest {
            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.awaitItem() // Ignore initial

                // Subscribing while offline should add the stream to the subscriptions reported in the
                // status.
                val subscription = database.syncStream("foo").subscribe()
                val status = turbine.awaitItem()
                status.forStream(subscription) shouldNotBeNull {}

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unsubscribing multiple times has no effect`() =
        databaseTest {
            val a = database.syncStream("a").subscribe()
            val aAgain = database.syncStream("a").subscribe()
            a.unsubscribe()
            a.unsubscribe()

            // Pretend the streams are expired - they should still be requested because the core
            // extension extends the lifetime of streams currently referenced before connecting.
            database.execute("UPDATE ps_stream_subscriptions SET expires_at = unixepoch() - 1000")

            database.connect(connector, options = getOptions())
            database.waitForStatusMatching { it.connected }
            requestedSyncStreams shouldHaveSingleElement {
                val streams = it.jsonObject["streams"]!!.jsonObject
                val subscriptions = streams["subscriptions"]!!.jsonArray
                subscriptions shouldHaveSize 1
                true
            }
            aAgain.unsubscribe()
        }

    @Test
    fun unsubscribeAll() =
        databaseTest {
            val a = database.syncStream("a").subscribe()
            database.syncStream("a").unsubscribeAll()

            // Despite a being active, it should not be requested.
            database.connect(connector, options = getOptions())
            database.waitForStatusMatching { it.connected }
            requestedSyncStreams shouldHaveSingleElement {
                val streams = it.jsonObject["streams"]!!.jsonObject
                val subscriptions = streams["subscriptions"]!!.jsonArray
                subscriptions shouldHaveSize 0
                true
            }
            a.unsubscribe()
        }
}

@OptIn(ExperimentalSerializationApi::class)
private fun checkpointLine(
    buckets: List<JsonObject>,
    streams: List<JsonObject>,
): JsonObject =
    buildJsonObject {
        put("checkpoint", checkpoint(buckets, streams))
    }

@OptIn(ExperimentalSerializationApi::class)
private fun checkpoint(
    buckets: List<JsonObject>,
    streams: List<JsonObject>,
): JsonObject =
    buildJsonObject {
        put("last_op_id", "0")
        put("buckets", buildJsonArray { addAll(buckets) })
        put("streams", buildJsonArray { addAll(streams) })
    }

private fun bucket(
    name: String,
    priority: Int,
    subscriptions: JsonArray? = null,
): JsonObject =
    buildJsonObject {
        put("bucket", name)
        put("priority", priority)
        put("checksum", 0)
        subscriptions?.let { put("subscriptions", it) }
    }

private fun stream(
    name: String,
    isDefault: Boolean,
): JsonObject =
    buildJsonObject {
        put("name", name)
        put("is_default", isDefault)
        put("errors", JsonArray(emptyList()))
    }

private fun defaultSubscription(index: Int): JsonObject = buildJsonObject { put("sub", index) }
