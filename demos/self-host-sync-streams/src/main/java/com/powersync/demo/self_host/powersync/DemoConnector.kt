package com.powersync.demo.self_host.powersync

import com.powersync.PowerSyncDatabase
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * A PowerSync connector that talks to the [demo Node.JS backend](https://github.com/powersync-ja/self-host-demo/tree/main/demos/nodejs).
 */
class DemoConnector(
    private val logger: co.touchlab.kermit.Logger,
) : PowerSyncBackendConnector() {
    private val client =
        HttpClient {
            install(DefaultRequest) {
                url("http://localhost:6060/")
            }

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    },
                )
            }
            install(Logging) {
                logger =
                    object : Logger {
                        override fun log(message: String) {
                            this@DemoConnector.logger.v { message }
                        }
                    }
                level = LogLevel.HEADERS
                filter { request ->
                    request.url.host.contains("ktor.io")
                }
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }
        }

    override suspend fun fetchCredentials(): PowerSyncCredentials? {
        @Serializable
        data class Response(
            val token: String,
        )

        val response = client.get("api/auth/token")
        check(response.status == HttpStatusCode.OK) {
            "Unexpected status code while fetching token: ${response.status}"
        }

        return PowerSyncCredentials(
            endpoint = "http://localhost:8080",
            response.body<Response>().token,
        )
    }

    override suspend fun uploadData(database: PowerSyncDatabase) {
        @Serializable
        data class Payload(
            val op: String,
            val table: String,
            val id: String,
            val data: JsonObject?,
        )

        @Serializable
        data class RequestBatch(
            val batch: List<Payload>,
        )

        database.getCrudTransactions().collect { tx ->
            val batch =
                buildList {
                    for (operation in tx.crud) {
                        add(
                            Payload(
                                op = operation.op.toJson(),
                                table = operation.table,
                                id = operation.id,
                                data = operation.opData?.jsonValues,
                            ),
                        )
                    }
                }

            val response =
                client.post("api/data") {
                    contentType(ContentType.Application.Json)
                    setBody(RequestBatch(batch))
                }
            check(response.status == HttpStatusCode.OK) {
                "Unexpected status code while upload crud tx: ${response.status}"
            }

            tx.complete(null)
        }
    }
}
