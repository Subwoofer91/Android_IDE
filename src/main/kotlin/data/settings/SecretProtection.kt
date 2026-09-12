package data.settings

import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.spec.AlgorithmParameterSpec

interface ApiKeyVault {
    fun put(alias: String, secret: CharArray)
    fun use(alias: String, action: (CharArray) -> Unit)
    fun delete(alias: String)
}

/**
 * Encrypts keys with a non-exportable AES key in the AndroidKeyStore provider.
 * Ciphertexts belong in app-private storage and must be excluded from backup.
 */
class AndroidKeystoreApiKeyVault(
    private val encryptedStore: EncryptedBlobStore,
    private val masterAlias: String = "android_ide_api_settings",
) : ApiKeyVault {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun put(alias: String, secret: CharArray) {
        ConfigValidation.key(secret)
        val bytes = secret.concatToString().toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            encryptedStore.write(alias, cipher.iv + cipher.doFinal(bytes))
        } finally { bytes.fill(0) }
    }

    override fun use(alias: String, action: (CharArray) -> Unit) {
        val blob = encryptedStore.read(alias) ?: error("API key is not configured")
        require(blob.size > 12) { "Invalid encrypted key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        val clearBytes = cipher.doFinal(blob.copyOfRange(12, blob.size))
        val chars = Charsets.UTF_8.decode(ByteBuffer.wrap(clearBytes)).let { buffer -> CharArray(buffer.length) { buffer[it] } }
        try { action(chars) } finally { chars.fill('\u0000'); clearBytes.fill(0) }
    }

    override fun delete(alias: String) = encryptedStore.delete(alias)

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(masterAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        // Reflection keeps this data module JVM-testable while still requiring the
        // Android KeyGenParameterSpec and non-exportable AndroidKeyStore at runtime.
        val keyProperties = Class.forName("android.security.keystore.KeyProperties")
        val encrypt = keyProperties.getField("PURPOSE_ENCRYPT").getInt(null)
        val decrypt = keyProperties.getField("PURPOSE_DECRYPT").getInt(null)
        val builderClass = Class.forName("android.security.keystore.KeyGenParameterSpec\$Builder")
        val builder = builderClass.getConstructor(String::class.java, Int::class.javaPrimitiveType)
            .newInstance(masterAlias, encrypt or decrypt)
        builderClass.getMethod("setBlockModes", Array<String>::class.java)
            .invoke(builder, arrayOf("GCM"))
        builderClass.getMethod("setEncryptionPaddings", Array<String>::class.java)
            .invoke(builder, arrayOf("NoPadding"))
        val spec = builderClass.getMethod("build").invoke(builder) as AlgorithmParameterSpec
        generator.init(spec)
        return generator.generateKey()
    }
}

interface EncryptedBlobStore {
    fun write(alias: String, encrypted: ByteArray)
    fun read(alias: String): ByteArray?
    fun delete(alias: String)
}

object SecretRedactor {
    fun redact(message: String?, secrets: Iterable<String> = emptyList()): String {
        var safe = message.orEmpty()
        secrets.filter { it.isNotEmpty() }.forEach { safe = safe.replace(it, "<redacted>") }
        safe = safe.replace(Regex("(?i)(authorization\\s*[:=]\\s*(?:bearer\\s+)?|api[_-]?key\\s*[:=]\\s*)[^\\s,;]+"), "$1<redacted>")
        safe = safe.replace(Regex("\\b(?:sk-[A-Za-z0-9_-]{8,}|[A-Za-z0-9_-]{32,})\\b"), "<redacted>")
        return safe.take(512)
    }
}