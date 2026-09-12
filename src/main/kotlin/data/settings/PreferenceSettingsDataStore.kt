package data.settings

/** Minimal boundary around AndroidX Preferences DataStore for platform wiring. */
interface PreferenceDataStoreDriver {
    suspend fun update(values: Map<String, String>)
    suspend fun read(): Map<String, String>
}

/** Persists only ordinary metadata. There is deliberately no API-key preference. */
class PreferenceSettingsDataStore(private val driver: PreferenceDataStoreDriver) : SettingsDataStore {
    override suspend fun save(config: ApiConfig) = driver.update(
        mapOf(
            "provider" to config.provider.name,
            "base_url" to config.baseUrl,
            "model" to config.model,
            "api_key_alias" to config.apiKeyAlias,
            "allow_insecure_http" to config.allowInsecureHttp.toString(),
        ),
    )

    override suspend fun load(): ApiConfig? {
        val values = driver.read()
        val provider = values["provider"] ?: return null
        return ApiConfig(
            provider = ProviderType.valueOf(provider),
            baseUrl = values.getValue("base_url"),
            model = values.getValue("model"),
            apiKeyAlias = values.getValue("api_key_alias"),
            allowInsecureHttp = values["allow_insecure_http"]?.toBooleanStrictOrNull() ?: false,
        )
    }
}
