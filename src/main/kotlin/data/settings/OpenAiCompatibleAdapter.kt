package data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeoutException

sealed interface ConnectionResult {
    data object Success : ConnectionResult
    data class Failure(val error: NetworkError, val safeMessage: String) : ConnectionResult
}
enum class NetworkError { UNAUTHORIZED, RATE_LIMITED, SERVER_ERROR, OFFLINE, TIMEOUT, INVALID_RESPONSE, UNKNOWN }

interface ApiConnectionTester { fun test(config: ApiConfig): ConnectionResult }

class OpenAiCompatibleAdapter(
    private val vault: ApiKeyVault,
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
    private val readTimeout: Duration = Duration.ofSeconds(20),
    private val totalTimeout: Duration = Duration.ofSeconds(30),
) : ApiConnectionTester {
    @Serializable private data class ModelsResponse(val data: List<Model> = emptyList())
    @Serializable private data class Model(val id: String)
    private val json = Json { ignoreUnknownKeys = true }

    override fun test(config: ApiConfig): ConnectionResult {
        val base = ConfigValidation.endpoint(config.baseUrl, config.allowInsecureHttp).uri.toString().trimEnd('/') + "/"
        val endpoint = URI(base).resolve("models")
        var result: ConnectionResult = ConnectionResult.Failure(NetworkError.UNKNOWN, "Connection test failed")
        vault.use(config.apiKeyAlias) { key ->
            val secret = key.concatToString()
            try {
                val request = HttpRequest.newBuilder(endpoint)
                    .header("Authorization", "Bearer $secret").header("Accept", "application/json")
                    .timeout(readTimeout).GET().build()
                val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(totalTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS).join()
                result = when (response.statusCode()) {
                    in 200..299 -> try {
                        json.decodeFromString<ModelsResponse>(response.body())
                        ConnectionResult.Success
                    } catch (_: Exception) { failure(NetworkError.INVALID_RESPONSE, "Service returned an invalid response") }
                    401, 403 -> failure(NetworkError.UNAUTHORIZED, "Authentication failed; check the API key")
                    429 -> failure(NetworkError.RATE_LIMITED, "Request limit reached; try again later")
                    in 500..599 -> failure(NetworkError.SERVER_ERROR, "Service is temporarily unavailable")
                    else -> failure(NetworkError.UNKNOWN, "Connection test failed (HTTP ${response.statusCode()})")
                }
            } catch (e: Throwable) { result = mapException(e, listOf(secret)) }
        }
        return result
    }

    private fun failure(error: NetworkError, message: String) = ConnectionResult.Failure(error, SecretRedactor.redact(message))

    internal fun mapException(error: Throwable, secrets: List<String> = emptyList()): ConnectionResult.Failure {
        var cause = error
        while ((cause is CompletionException || cause is java.util.concurrent.ExecutionException) && cause.cause != null) cause = cause.cause!!
        val type = when (cause) {
            is java.net.http.HttpTimeoutException, is TimeoutException -> NetworkError.TIMEOUT
            is ConnectException, is java.net.UnknownHostException, is java.net.NoRouteToHostException -> NetworkError.OFFLINE
            is IOException -> NetworkError.OFFLINE
            else -> NetworkError.UNKNOWN
        }
        val message = when (type) {
            NetworkError.TIMEOUT -> "Connection timed out"
            NetworkError.OFFLINE -> "No network connection or service is unreachable"
            else -> "Connection test failed"
        }
        return ConnectionResult.Failure(type, SecretRedactor.redact(message, secrets))
    }
}