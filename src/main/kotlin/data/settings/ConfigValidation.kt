package data.settings

import java.net.URI

class ConfigValidationException(message: String) : IllegalArgumentException(message)

object ConfigValidation {
    const val MAX_URL_LENGTH = 2048
    const val MAX_MODEL_LENGTH = 128
    const val MAX_KEY_LENGTH = 4096

    fun endpoint(raw: String, allowInsecureHttp: Boolean = false): ValidatedEndpoint {
        if (raw.length !in 1..MAX_URL_LENGTH) invalid("Base URL length is invalid")
        if (raw.any { it.isISOControl() }) invalid("Base URL contains control characters")
        val uri = try { URI(raw) } catch (_: Exception) { invalid("Base URL is malformed") }
        if (!uri.isAbsolute || uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) {
            invalid("Base URL must be an absolute server URL without credentials or fragments")
        }
        if (uri.scheme.equals("https", true)) return ValidatedEndpoint(uri, null)
        if (uri.scheme.equals("http", true) && allowInsecureHttp) {
            return ValidatedEndpoint(uri, "Warning: HTTP sends requests without transport encryption, including API credentials.")
        }
        invalid("HTTPS is required; explicitly enable insecure HTTP only for a trusted development service")
    }

    fun model(value: String): String = value.trim().also {
        if (it.length !in 1..MAX_MODEL_LENGTH || it.any(Char::isISOControl)) invalid("Model name is invalid")
    }

    fun key(value: CharArray): CharArray = value.also {
        if (it.size !in 1..MAX_KEY_LENGTH || it.any(Char::isISOControl)) invalid("API key is invalid")
    }

    private fun invalid(message: String): Nothing = throw ConfigValidationException(message)
}
