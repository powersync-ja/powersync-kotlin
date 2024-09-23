package com.powersync.utils

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
    fun testMapWithUnsupportedType() {
        val testMap = mapOf("unsupported" to object {})
        val result = convertMapToJson(testMap)
        assertEquals(JsonObject(mapOf("unsupported" to JsonPrimitive(null))), result)
    }
}