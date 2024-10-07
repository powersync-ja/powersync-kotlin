package com.powersync.utils


import kotlinx.serialization.json.*
import kotlin.test.*

class JsonTest {
    @Test
    fun testNumberToJsonElement() {
        val number = JsonParam.Number(42)
        val jsonElement = number.toJsonElement()
        assertTrue(jsonElement is JsonPrimitive)
        assertEquals(42, jsonElement.int)
    }

    @Test
    fun testStringToJsonElement() {
        val string = JsonParam.String("test")
        val jsonElement = string.toJsonElement()
        assertTrue(jsonElement is JsonPrimitive)
        assertEquals("test", jsonElement.content)
    }

    @Test
    fun testBooleanToJsonElement() {
        val boolean = JsonParam.Boolean(true)
        val jsonElement = boolean.toJsonElement()
        assertTrue(jsonElement is JsonPrimitive)
        assertTrue(jsonElement.boolean)
    }

    @Test
    fun testMapToJsonElement() {
        val map = JsonParam.Map(mapOf(
            "key1" to JsonParam.String("value1"),
            "key2" to JsonParam.Number(42)
        ))
        val jsonElement = map.toJsonElement()
        assertTrue(jsonElement is JsonObject)
        assertEquals("value1", jsonElement["key1"]?.jsonPrimitive?.content)
        assertEquals(42, jsonElement["key2"]?.jsonPrimitive?.int)
    }

    @Test
    fun testListToJsonElement() {
        val list = JsonParam.Collection(listOf(
            JsonParam.String("item1"),
            JsonParam.Number(42)
        ))
        val jsonElement = list.toJsonElement()
        assertTrue(jsonElement is JsonArray)
        assertEquals("item1", jsonElement[0].jsonPrimitive.content)
        assertEquals(42, jsonElement[1].jsonPrimitive.int)
    }

    @Test
    fun testJsonElementParamToJsonElement() {
        val originalJson = buildJsonObject {
            put("key", "value")
        }
        val jsonElementParam = JsonParam.JsonElement(originalJson)
        val jsonElement = jsonElementParam.toJsonElement()
        assertEquals(originalJson, jsonElement)
    }

    @Test
    fun testNullToJsonElement() {
        val nullParam = JsonParam.Null
        val jsonElement = nullParam.toJsonElement()
        assertTrue(jsonElement is JsonNull)
    }

    @Test
    fun testMapToJsonObject() {
        val params = mapOf(
            "string" to JsonParam.String("value"),
            "number" to JsonParam.Number(42),
            "boolean" to JsonParam.Boolean(true),
            "null" to JsonParam.Null
        )
        val jsonObject = params.toJsonObject()
        assertEquals("value", jsonObject["string"]?.jsonPrimitive?.content)
        assertEquals(42, jsonObject["number"]?.jsonPrimitive?.int)
        assertEquals(true, jsonObject["boolean"]?.jsonPrimitive?.boolean)
        assertTrue(jsonObject["null"] is JsonNull)
    }

    @Test
    fun testComplexNestedMapToJsonObject() {
        val complexNestedMap = mapOf(
            "string" to JsonParam.String("value"),
            "number" to JsonParam.Number(42),
            "boolean" to JsonParam.Boolean(true),
            "null" to JsonParam.Null,
            "nestedMap" to JsonParam.Map(mapOf(
                "list" to JsonParam.Collection(listOf(
                    JsonParam.Number(1),
                    JsonParam.String("two"),
                    JsonParam.Boolean(false)
                )),
                "deeplyNested" to JsonParam.Map(mapOf(
                    "jsonElement" to JsonParam.JsonElement(buildJsonObject {
                        put("key", "value")
                        put("array", buildJsonArray {
                            add(1)
                            add("string")
                            add(true)
                        })
                    }),
                    "mixedList" to JsonParam.Collection(arrayListOf(
                        JsonParam.Number(3.14),
                        JsonParam.Map(mapOf(
                            "key" to JsonParam.String("nestedValue")
                        )),
                        JsonParam.Null
                    )
                    )
                ))
            ))
        )

        val jsonObject = complexNestedMap.toJsonObject()

        // Verify top-level elements
        assertEquals("value", jsonObject["string"]?.jsonPrimitive?.content)
        assertEquals(42, jsonObject["number"]?.jsonPrimitive?.int)
        assertEquals(true, jsonObject["boolean"]?.jsonPrimitive?.boolean)
        assertTrue(jsonObject["null"] is JsonNull)

        // Verify nested map
        val nestedMap = jsonObject["nestedMap"]?.jsonObject
        assertNotNull(nestedMap)

        // Verify nested list
        val nestedList = nestedMap["list"]?.jsonArray
        assertNotNull(nestedList)
        assertEquals(3, nestedList.size)
        assertEquals(1, nestedList[0].jsonPrimitive.int)
        assertEquals("two", nestedList[1].jsonPrimitive.content)
        assertEquals(false, nestedList[2].jsonPrimitive.boolean)

        // Verify deeply nested map
        val deeplyNested = nestedMap["deeplyNested"]?.jsonObject
        assertNotNull(deeplyNested)

        // Verify JsonElement
        val jsonElement = deeplyNested["jsonElement"]?.jsonObject
        assertNotNull(jsonElement)
        assertEquals("value", jsonElement["key"]?.jsonPrimitive?.content)
        val jsonElementArray = jsonElement["array"]?.jsonArray
        assertNotNull(jsonElementArray)
        assertEquals(3, jsonElementArray.size)
        assertEquals(1, jsonElementArray[0].jsonPrimitive.int)
        assertEquals("string", jsonElementArray[1].jsonPrimitive.content)
        assertEquals(true, jsonElementArray[2].jsonPrimitive.boolean)

        // Verify mixed list
        val mixedList = deeplyNested["mixedList"]?.jsonArray
        assertNotNull(mixedList)
        assertEquals(3, mixedList.size)
        assertEquals(3.14, mixedList[0].jsonPrimitive.double)
        val nestedMapInList = mixedList[1].jsonObject
        assertNotNull(nestedMapInList)
        assertEquals("nestedValue", nestedMapInList["key"]?.jsonPrimitive?.content)
        assertTrue(mixedList[2] is JsonNull)
    }
}