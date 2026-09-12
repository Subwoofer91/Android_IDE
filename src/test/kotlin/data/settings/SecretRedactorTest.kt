package data.settings

import kotlin.test.*

class SecretRedactorTest {
    @Test fun `redacts known and credential shaped secrets`() {
        val key = "sk-abcdefgh12345678"
        val output = SecretRedactor.redact("Authorization: Bearer $key api_key=anotherSecret", listOf(key))
        assertFalse(output.contains(key))
        assertFalse(output.contains("anotherSecret"))
        assertTrue(output.contains("<redacted>"))
    }

    @Test fun `limits throwable-safe output length`() {
        assertEquals(512, SecretRedactor.redact("x".repeat(1000)).length)
    }
}
