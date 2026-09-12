package data.settings

/** Provider-neutral settings consumed by protocol adapters, never directly by UI code. */
data class ApiConfig(
    val provider: ProviderType,
    val baseUrl: String,
    val model: String,
    /** Alias into [ApiKeyVault], not the secret itself. */
    val apiKeyAlias: String,
    val allowInsecureHttp: Boolean = false,
)

enum class ProviderType { OPENAI_COMPATIBLE, CUSTOM }

data class ApiConfigDraft(
    val provider: ProviderType,
    val baseUrl: String,
    val model: String,
    val apiKey: CharArray,
    val allowInsecureHttp: Boolean = false,
) : AutoCloseable {
    override fun close() = apiKey.fill('\u0000')
}

data class ValidatedEndpoint(val uri: java.net.URI, val insecureWarning: String?)
