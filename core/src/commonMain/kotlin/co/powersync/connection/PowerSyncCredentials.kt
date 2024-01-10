package co.powersync.connection

import io.ktor.util.decodeBase64String
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Temporary credentials to connect to the PowerSync service.
 */

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class PowerSyncCredentials(
    /**
     * PowerSync endpoint, e.g. "https://myinstance.powersync.co".
     */
    val endpoint: String,
    /**
     * Temporary token to authenticate against the service.
     */
    val token: String,
    /**
     * User ID.
     */
    @JsonNames("user_id") val userId: String,
    /**
     * When the token expires. Only use for debugging purposes.
     */
    val expiresAt: Instant?
) {
    companion object {
        /**
         * Get an expiry date from a JWT token, if specified.
         *
         * The token is not validated in any way.
         */
        fun getExpiryDate(token: String): Instant? {
            return try {
                val payload = decodeJwt(token)
                val expiry = payload["exp"]?.jsonPrimitive?.long
                if (expiry != null) {
                    Instant.fromEpochSeconds(expiry)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        fun decodeJwt(jwt: String): JsonObject {
            val parts = jwt.split('.')
            check(parts.size == 3) { "Invalid JWT" }

            val payload = parts[1].decodeBase64String()

            val json = Json { ignoreUnknownKeys = true }
            return json.parseToJsonElement(payload).jsonObject
        }
    }

    override fun toString(): String {
        return "PowerSyncCredentials<endpoint: $endpoint userId: $userId expiresAt: $expiresAt>"
    }

    fun endpointUri(path: String): String {
        return "$endpoint/$path"
    }
}