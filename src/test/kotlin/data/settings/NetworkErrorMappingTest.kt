package data.settings

import java.net.ConnectException
import java.net.http.HttpTimeoutException
import kotlin.test.*

class NetworkErrorMappingTest {
    private val adapter = OpenAiCompatibleAdapter(object : ApiKeyVault {
        override fun put(alias: String, secret: CharArray) = Unit
        override fun use(alias: String, action: (CharArray) -> Unit) = Unit
        override fun delete(alias: String) = Unit
    })

    @Test fun `maps timeout`() = assertEquals(NetworkError.TIMEOUT, adapter.mapException(HttpTimeoutException("secret")).error)
    @Test fun `maps disconnected host`() = assertEquals(NetworkError.OFFLINE, adapter.mapException(ConnectException("secret")).error)
    @Test fun `does not expose exception details`() {
        val secret = "sk-super-secret-value"
        assertFalse(adapter.mapException(IllegalStateException(secret), listOf(secret)).safeMessage.contains(secret))
    }
}
