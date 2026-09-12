package data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Data-only, versioned import. JSON is never evaluated or used for class loading. */
object ConfigImport {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = true
    }

    @Serializable
    private data class SchemaV1(
        val schemaVersion: Int,
        val provider: String,
        val baseUrl: String,
        val model: String,
        val apiKey: String,
        val allowInsecureHttp: Boolean = false,
    )

    fun parse(input: String): ApiConfigDraft {
        if (input.toByteArray(Charsets.UTF_8).size > 16 * 1024) throw ConfigValidationException("Configuration file is too large")
        val value = try { json.decodeFromString<SchemaV1>(input) }
        catch (_: SerializationException) { throw ConfigValidationException("Configuration JSON does not match schema v1") }
        if (value.schemaVersion != 1) throw ConfigValidationException("Unsupported configuration schema version")
        val provider = try { ProviderType.valueOf(value.provider) }
        catch (_: IllegalArgumentException) { throw ConfigValidationException("Unknown provider type") }
        ConfigValidation.endpoint(value.baseUrl, value.allowInsecureHttp)
        ConfigValidation.model(value.model)
        val key = value.apiKey.toCharArray()
        ConfigValidation.key(key)
        return ApiConfigDraft(provider, value.baseUrl, value.model.trim(), key, value.allowInsecureHttp)
    }
}
