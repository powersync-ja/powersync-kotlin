package co.powersync

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class Greeting {
    private val client = HttpClient()
    private val platform = getPlatform()

    suspend fun greet(): String {
        val response = client.get("https://ktor.io/docs/")
        return response.bodyAsText()
    }

//    fun greet(): String {
//        return "Hello, ${platform.name}!"
//    }
}