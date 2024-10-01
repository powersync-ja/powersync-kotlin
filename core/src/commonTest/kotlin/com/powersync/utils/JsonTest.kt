package com.powersync.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

class JsonTest {
    @Test
    fun testEmptyMap() {
        val emptyMap = emptyMap<String, Any>()
        val result = convertMapToJson(emptyMap)
        assertEquals(0, result.size)
    }

    @Test
    fun testNull() {
        val result = convertMapToJson(null)
        assertEquals(JsonObject(mapOf()), result)
    }

    @Test
    fun testIntegerMap() {
        val testMap = mapOf("int" to 1)
        val result = convertMapToJson(testMap)
        assertEquals(JsonObject(mapOf("int" to JsonPrimitive(1))), result)
    }

    @Test
    fun testStringMap() {
        val testMap = mapOf("string" to "string")
        val result = convertMapToJson(testMap)
        assertEquals(JsonObject(mapOf("string" to JsonPrimitive("string"))), result)
    }

    @Test
    fun testLongMap() {
        val testMap = mapOf("double" to 123L)
        val result = convertMapToJson(testMap)
        assertEquals(JsonObject(mapOf("double" to JsonPrimitive(123L))), result)
    }

    @Test
    fun testBooleanMap() {
        val testMap = mapOf("boolean" to false)
        val result = convertMapToJson(testMap)
        assertEquals(JsonObject(mapOf("boolean" to JsonPrimitive(false))), result)
    }

    @Test
    fun testDoubleMap() {
        val testMap = mapOf("double" to 0.02)
        val result = convertMapToJson(testMap)
        assertEquals(JsonObject(mapOf("double" to JsonPrimitive(0.02))), result)
    }

    @Test
    fun testArrayMap() {
        val testMap = mapOf("array" to arrayOf(1, 2, 3))
        val result = convertMapToJson(testMap)
        assertEquals(JsonObject(mapOf("array" to JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2), JsonPrimitive(3))))), result)
    }

    @Test
    fun testListMap() {
        val testMap = mapOf("list" to listOf("a", "b", "c"))
        val result = convertMapToJson(testMap)
        assertEquals(JsonObject(mapOf("list" to JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"), JsonPrimitive("c"))))), result)
    }

    @Test
    fun testNestedMap() {
        val testMap = mapOf(
            "nested" to mapOf(
                "int" to 1,
                "string" to "value"
            )
        )
        val result = convertMapToJson(testMap)
        assertEquals(JsonObject(mapOf(
            "nested" to JsonObject(mapOf(
                "int" to JsonPrimitive(1),
                "string" to JsonPrimitive("value")
            ))
        )), result)
    }

    @Test
    fun testComplexNestedStructure() {
        val testMap = mapOf(
            "string" to "value",
            "int" to 42,
            "list" to listOf(1, "two", 3.0),
            "nestedMap" to mapOf(
                "array" to arrayOf(true, false),
                "nestedList" to listOf(
                    mapOf("key" to "value"),
                    listOf(1, 2, 3)
                )
            )
        )
        val result = convertMapToJson(testMap)

        val expected = JsonObject(mapOf(
            "string" to JsonPrimitive("value"),
            "int" to JsonPrimitive(42),
            "list" to JsonArray(listOf(JsonPrimitive(1), JsonPrimitive("two"), JsonPrimitive(3.0))),
            "nestedMap" to JsonObject(mapOf(
                "array" to JsonArray(listOf(JsonPrimitive(true), JsonPrimitive(false))),
                "nestedList" to JsonArray(listOf(
                    JsonObject(mapOf("key" to JsonPrimitive("value"))),
                    JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2), JsonPrimitive(3)))
                ))
            ))
        ))

        assertEquals(expected, result)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun testMapWithUnsupportedType() {
        val testMap = mapOf("unsupported" to object {})
        val result = convertMapToJson(testMap)
        assertEquals(JsonObject(mapOf("unsupported" to JsonPrimitive(null))), result)
    }
}