package data.settings

import java.util.UUID

/** Adapter implemented using Preferences DataStore; it must never receive API key material. */
interface SettingsDataStore {
    suspend fun save(config: ApiConfig)
    suspend fun load(): ApiConfig?
}

class ApiSettingsRepository(private val dataStore: SettingsDataStore, private val vault: ApiKeyVault) {
    suspend fun save(draft: ApiConfigDraft): ApiConfig {
        ConfigValidation.endpoint(draft.baseUrl, draft.allowInsecureHttp)
        ConfigValidation.model(draft.model)
        ConfigValidation.key(draft.apiKey)
        val alias = "api-${UUID.randomUUID()}"
        vault.put(alias, draft.apiKey)
        val config = ApiConfig(draft.provider, draft.baseUrl, draft.model.trim(), alias, draft.allowInsecureHttp)
        try { dataStore.save(config) } catch (failure: Throwable) { vault.delete(alias); throw failure }
        return config
    }
}
