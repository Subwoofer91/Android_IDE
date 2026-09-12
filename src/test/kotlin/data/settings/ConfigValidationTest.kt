package data.settings

import kotlin.test.*

class ConfigValidationTest {
    @Test fun `https is accepted by default`() = assertNull(ConfigValidation.endpoint("https://example.test/v1").insecureWarning)

    @Test fun `http requires explicit opt in and produces warning`() {
        assertFailsWith<ConfigValidationException> { ConfigValidation.endpoint("http://example.test") }
        assertNotNull(ConfigValidation.endpoint("http://localhost:8080", true).insecureWarning)
    }

    @Test fun `unsafe URL forms are rejected`() {
        listOf("file:///tmp/key", "https://user:pass@example.test", "//example.test", "https://example.test/#fragment")
            .forEach { assertFailsWith<ConfigValidationException>(it) { ConfigValidation.endpoint(it) } }
    }

    @Test fun `length bounds are enforced`() {
        assertFailsWith<ConfigValidationException> { ConfigValidation.model("x".repeat(ConfigValidation.MAX_MODEL_LENGTH + 1)) }
        assertFailsWith<ConfigValidationException> { ConfigValidation.key(CharArray(ConfigValidation.MAX_KEY_LENGTH + 1) { 'x' }) }
    }
}
