package ia.off

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class MemoriaDeviceBinding(
    val serverBaseUrl: String,
    val deviceId: String,
)

data class MemoriaDeviceIdentity(
    val publicKey: String,
    val binding: MemoriaDeviceBinding?,
)

class MemoriaDeviceIdentityStore(context: Context) {
    companion object {
        private const val PREFS = "offia-memoria-device-v1"
        private const val PUBLIC_KEY = "ed25519_public_key"
        private const val PRIVATE_KEY_CIPHERTEXT = "ed25519_private_key_ciphertext"
        private const val PRIVATE_KEY_IV = "ed25519_private_key_iv"
        private const val SERVER_BASE_URL = "server_base_url"
        private const val DEVICE_ID = "device_id"
        private const val AES_ALIAS = "offia-memoria-device-wrap-v1"
        private val ED25519_SPKI_PREFIX = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03,
            0x2b, 0x65, 0x70, 0x03, 0x21, 0x00,
        )
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun loadOrCreate(): MemoriaDeviceIdentity {
        val existingPublic = prefs.getString(PUBLIC_KEY, null)?.trim().orEmpty()
        val existingCiphertext = prefs.getString(PRIVATE_KEY_CIPHERTEXT, null)?.trim().orEmpty()
        val existingIv = prefs.getString(PRIVATE_KEY_IV, null)?.trim().orEmpty()

        if (existingPublic.isNotBlank() || existingCiphertext.isNotBlank() || existingIv.isNotBlank()) {
            require(existingPublic.isNotBlank() && existingCiphertext.isNotBlank() && existingIv.isNotBlank()) {
                "Estado de identidade Memoria.ia incompleto; recuse regeneração silenciosa"
            }
            // Verify that the wrapped private key can actually be opened before
            // presenting the identity as usable.
            decryptPrivateKey()
            return MemoriaDeviceIdentity(existingPublic, loadBinding())
        }

        val pair = try {
            KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        } catch (error: Exception) {
            throw IllegalStateException("Este Android não disponibiliza geração Ed25519", error)
        }
        val rawPublic = rawEd25519PublicKey(pair.public.encoded)
        val publicText = "ed25519:" + base64Url(rawPublic)
        val wrapped = encryptPrivateKey(pair.private.encoded)

        check(
            prefs.edit()
                .putString(PUBLIC_KEY, publicText)
                .putString(PRIVATE_KEY_CIPHERTEXT, Base64.getEncoder().encodeToString(wrapped.first))
                .putString(PRIVATE_KEY_IV, Base64.getEncoder().encodeToString(wrapped.second))
                .commit(),
        ) { "Falha ao persistir identidade Memoria.ia" }

        return MemoriaDeviceIdentity(publicText, null)
    }

    @Synchronized
    fun bind(serverBaseUrl: String, deviceId: String): MemoriaDeviceIdentity {
        val identity = loadOrCreate()
        val normalizedServer = normalizeServerBaseUrl(serverBaseUrl)
        val normalizedDevice = deviceId.trim()
        require(normalizedDevice.isNotBlank()) { "deviceId vazio" }

        val current = identity.binding
        if (current != null) {
            require(current.serverBaseUrl == normalizedServer && current.deviceId == normalizedDevice) {
                "Identidade já está vinculada a outro dispositivo/servidor; recuse substituição silenciosa"
            }
            return identity
        }

        check(
            prefs.edit()
                .putString(SERVER_BASE_URL, normalizedServer)
                .putString(DEVICE_ID, normalizedDevice)
                .commit(),
        ) { "Falha ao persistir vínculo Memoria.ia" }
        return MemoriaDeviceIdentity(
            publicKey = identity.publicKey,
            binding = MemoriaDeviceBinding(normalizedServer, normalizedDevice),
        )
    }

    @Synchronized
    fun loadBinding(): MemoriaDeviceBinding? {
        val server = prefs.getString(SERVER_BASE_URL, null)?.trim().orEmpty()
        val device = prefs.getString(DEVICE_ID, null)?.trim().orEmpty()
        if (server.isBlank() && device.isBlank()) return null
        require(server.isNotBlank() && device.isNotBlank()) {
            "Estado de vínculo Memoria.ia incompleto"
        }
        return MemoriaDeviceBinding(
            serverBaseUrl = normalizeServerBaseUrl(server),
            deviceId = device,
        )
    }

    @Synchronized
    fun sign(message: ByteArray): ByteArray {
        require(message.isNotEmpty()) { "Mensagem Ed25519 vazia" }
        val signature = Signature.getInstance("Ed25519")
        signature.initSign(decryptPrivateKey())
        signature.update(message)
        return signature.sign()
    }

    private fun encryptPrivateKey(privatePkcs8: ByteArray): Pair<ByteArray, ByteArray> {
        require(privatePkcs8.isNotEmpty()) { "Chave privada Ed25519 vazia" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        return cipher.doFinal(privatePkcs8) to cipher.iv
    }

    private fun decryptPrivateKey(): PrivateKey {
        val ciphertextText = prefs.getString(PRIVATE_KEY_CIPHERTEXT, null)
            ?: error("Chave privada Ed25519 ausente")
        val ivText = prefs.getString(PRIVATE_KEY_IV, null)
            ?: error("IV da chave Ed25519 ausente")
        val ciphertext = try {
            Base64.getDecoder().decode(ciphertextText)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("Chave privada Ed25519 corrompida", error)
        }
        val iv = try {
            Base64.getDecoder().decode(ivText)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("IV Ed25519 corrompido", error)
        }
        require(iv.size == 12) { "IV AES-GCM da identidade possui tamanho inválido" }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        val encoded = try {
            cipher.doFinal(ciphertext)
        } catch (error: Exception) {
            throw IllegalStateException("Não foi possível abrir a identidade Ed25519 persistida", error)
        }
        return KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(encoded))
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(AES_ALIAS, null)
        if (existing != null) {
            require(existing is SecretKey) { "Alias de proteção Memoria.ia não contém chave simétrica" }
            return existing
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                AES_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    internal companion object Codec {
        fun normalizeServerBaseUrl(value: String): String =
            value.trim().trimEnd('/').also {
                require(it.startsWith("http://") || it.startsWith("https://")) {
                    "Memoria.ia Server URL deve usar http(s)"
                }
            }

        fun rawEd25519PublicKey(encoded: ByteArray): ByteArray {
            require(encoded.size == ED25519_SPKI_PREFIX.size + 32) {
                "Chave pública Ed25519 X.509 possui tamanho inesperado"
            }
            require(encoded.copyOfRange(0, ED25519_SPKI_PREFIX.size).contentEquals(ED25519_SPKI_PREFIX)) {
                "Chave pública não usa SubjectPublicKeyInfo Ed25519 esperado"
            }
            return encoded.copyOfRange(ED25519_SPKI_PREFIX.size, encoded.size)
        }

        fun base64Url(value: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    }
}
