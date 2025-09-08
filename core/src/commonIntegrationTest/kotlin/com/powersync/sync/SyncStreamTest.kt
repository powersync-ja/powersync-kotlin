package com.powersync.sync

import app.cash.turbine.turbineScope
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.StreamPriority
import com.powersync.testutils.bucket
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
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "1",
                            checksums =
                                listOf(
                                    bucket(
                                        "a",
                                        0,
                                        subscriptions =
                                            buildJsonArray {
                                                add(defaultSubscription(0))
                                            },
                                    ),
                                    bucket(
                                        "b",
                                        0,
                                        priority = StreamPriority(1),
                                        subscriptions =
                                            buildJsonArray {
                                                add(defaultSubscription(1))
                                            },
                                    ),
                                ),
                            streams = listOf(stream("stream", false)),
                        ),
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

                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "1",
                            checksums = listOf(),
                            streams = listOf(stream("default_stream", true)),
                        ),
                    ),
                )

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

                // Adding the subscription should reconnect
                turbine.waitFor { it.connected && !it.downloading }
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
