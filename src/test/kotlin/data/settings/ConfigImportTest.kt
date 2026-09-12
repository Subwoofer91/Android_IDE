package data.settings

import kotlin.test.*

class ConfigImportTest {
    @Test fun `parses strict schema v1`() {
        ConfigImport.parse("""{"schemaVersion":1,"provider":"OPENAI_COMPATIBLE","baseUrl":"https://api.example/v1/","model":"small","apiKey":"secret"}""").use {
            assertEquals("small", it.model)
            assertContentEquals("secret".toCharArray(), it.apiKey)
        }
    }

    @Test fun `rejects unknown fields and versions`() {
        assertFailsWith<ConfigValidationException> { ConfigImport.parse("""{"schemaVersion":1,"provider":"CUSTOM","baseUrl":"https://x.test","model":"m","apiKey":"k","script":"run()"}""") }
        assertFailsWith<ConfigValidationException> { ConfigImport.parse("""{"schemaVersion":2,"provider":"CUSTOM","baseUrl":"https://x.test","model":"m","apiKey":"k"}""") }
    }

    @Test fun `rejects invalid field types`() {
        assertFailsWith<ConfigValidationException> { ConfigImport.parse("""{"schemaVersion":1,"provider":"CUSTOM","baseUrl":"https://x.test","model":3,"apiKey":"k"}""") }
    }
}
