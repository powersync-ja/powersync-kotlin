import com.powersync.bucket.BucketRequest
import com.powersync.sync.StreamingSyncRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

class StreamingSyncRequestTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testSerialization() {
        val request = StreamingSyncRequest(
            buckets = listOf(BucketRequest("table1", "op1"), BucketRequest("table2", "op2")),
            includeChecksum = true,
            clientId = "client123",
            parameters = JsonObject(mapOf("param1" to JsonPrimitive("value1")))
        )

        val serialized = json.encodeToString(request)

        val expected = """
            {"buckets":[{"name":"table1","after":"op1"},{"name":"table2","after":"op2"}],"client_id":"client123","parameters":{"param1":"value1"}}
        """.trimIndent().replace("\n", "")

        assertEquals(expected, serialized)
    }

    @Test
    fun testDeserialization() {
        val jsonString = """
            {
                "buckets": [{"name": "table1", "after": "op1"}, {"name": "table2", "after": "op2"}],
                "include_checksum": false,
                "client_id": "client456",
                "parameters": {"param2": "value2"},
                "raw_data": true
            }
        """.trimIndent()

        val deserialized = json.decodeFromString<StreamingSyncRequest>(jsonString)

        assertEquals(2, deserialized.buckets.size)
        assertEquals("table1", deserialized.buckets[0].name)
        assertEquals("op1", deserialized.buckets[0].after)
        assertEquals("table2", deserialized.buckets[1].name)
        assertEquals("op2", deserialized.buckets[1].after)
        assertFalse(deserialized.includeChecksum)
        assertEquals("client456", deserialized.clientId)
        assertEquals(JsonPrimitive("value2"), deserialized.parameters?.get("param2"))
    }

    @Test
    fun testDefaultValues() {
        val request = StreamingSyncRequest(
            buckets = listOf(),
            clientId = "client789"
        )

        assertTrue(request.includeChecksum)
        assertEquals(request.parameters, JsonObject(mapOf()))

        val serialized = json.encodeToString(request)
        val expected = """
            {"buckets":[],"client_id":"client789"}
        """.trimIndent().replace("\n", "")
        assertEquals(serialized, expected)
    }
}