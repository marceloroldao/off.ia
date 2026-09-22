package ia.off

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlin.math.max

data class MemoriaEnrollmentResult(
    val deviceId: String,
    val status: String,
)

internal interface MemoriaDeviceAuthTransport {
    suspend fun claim(
        serverBaseUrl: String,
        enrollmentCode: String,
        deviceName: String,
        publicKey: String,
    ): MemoriaEnrollmentResult

    suspend fun challenge(
        serverBaseUrl: String,
        deviceId: String,
    ): MemoriaDeviceChallenge

    suspend fun verify(
        serverBaseUrl: String,
        deviceId: String,
        challengeId: String,
        signature: String,
    ): MemoriaDeviceToken
}

data class MemoriaDeviceChallenge(
    val challengeId: String,
    val signingMessage: String,
)

data class MemoriaDeviceToken(
    val token: String,
    val expiresInSeconds: Long,
)

internal class HttpMemoriaDeviceAuthTransport : MemoriaDeviceAuthTransport {
    override suspend fun claim(
        serverBaseUrl: String,
        enrollmentCode: String,
        deviceName: String,
        publicKey: String,
    ): MemoriaEnrollmentResult = withContext(Dispatchers.IO) {
        val root = post(
            url = serverBaseUrl + "/api/server/v1/device-enrollment/claim",
            payload = JSONObject()
                .put("enrollment_code", enrollmentCode)
                .put("name", deviceName)
                .put("public_key", publicKey)
                .put(
                    "capabilities",
                    JSONObject()
                        .put("cpu", "arm64")
                        .put("models", org.json.JSONArray()),
                )
                .put(
                    "versions",
                    JSONObject()
                        .put("offia", BuildConfig.VERSION_NAME)
                        .put("offia_commit", BuildConfig.OFFIA_COMMIT),
                ),
            authorization = null,
            expectedCodes = setOf(201),
        )
        val device = root.optJSONObject("device")
            ?: error("Resposta de enrollment sem device")
        val deviceId = device.optString("device_id").trim()
        require(deviceId.isNotBlank()) { "Resposta de enrollment sem device_id" }
        MemoriaEnrollmentResult(
            deviceId = deviceId,
            status = root.optString("status", "pending_approval"),
        )
    }

    override suspend fun challenge(
        serverBaseUrl: String,
        deviceId: String,
    ): MemoriaDeviceChallenge = withContext(Dispatchers.IO) {
        val root = post(
            url = serverBaseUrl + "/api/server/v1/device-auth/challenge",
            payload = JSONObject().put("device_id", deviceId),
            authorization = null,
            expectedCodes = setOf(200),
        )
        val challengeId = root.optString("challenge_id").trim()
        val signingMessage = root.optString("signing_message")
        require(challengeId.isNotBlank()) { "Challenge sem challenge_id" }
        require(signingMessage.isNotBlank()) { "Challenge sem signing_message" }
        MemoriaDeviceChallenge(challengeId, signingMessage)
    }

    override suspend fun verify(
        serverBaseUrl: String,
        deviceId: String,
        challengeId: String,
        signature: String,
    ): MemoriaDeviceToken = withContext(Dispatchers.IO) {
        val root = post(
            url = serverBaseUrl + "/api/server/v1/device-auth/verify",
            payload = JSONObject()
                .put("device_id", deviceId)
                .put("challenge_id", challengeId)
                .put("signature", signature),
            authorization = null,
            expectedCodes = setOf(200),
        )
        val token = root.optString("token").trim()
        val expires = root.optLong("expires_in", 0L)
        require(token.isNotBlank()) { "Verificação sem Device token" }
        require(expires > 0L) { "Device token sem validade positiva" }
        MemoriaDeviceToken(token, expires)
    }

    private fun post(
        url: String,
        payload: JSONObject,
        authorization: String?,
        expectedCodes: Set<Int>,
    ): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!authorization.isNullOrBlank()) {
                setRequestProperty("Authorization", authorization)
            }
        }

        return try {
            connection.outputStream.use {
                it.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in expectedCodes) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in expectedCodes) {
                val detail = runCatching {
                    JSONObject(body).let { json ->
                        json.optString("detail").ifBlank { json.optString("error") }
                    }
                }.getOrNull().orEmpty()
                throw IllegalStateException(
                    "Memoria.ia Server rejeitou autenticação (HTTP $code)" +
                        detail.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty(),
                )
            }
            JSONObject(body.ifBlank { "{}" })
        } finally {
            connection.disconnect()
        }
    }
}

class MemoriaDeviceEnrollmentClient internal constructor(
    private val identityStore: MemoriaDeviceIdentityProvider,
    private val transport: MemoriaDeviceAuthTransport,
) {
    constructor(
        identityStore: MemoriaDeviceIdentityProvider,
    ) : this(identityStore, HttpMemoriaDeviceAuthTransport())

    suspend fun claim(
        serverBaseUrl: String,
        enrollmentCode: String,
        deviceName: String,
    ): MemoriaEnrollmentResult {
        val server = MemoriaDeviceIdentityStore.Codec.normalizeServerBaseUrl(serverBaseUrl)
        val code = enrollmentCode.trim()
        val name = deviceName.trim()
        require(code.length in 24..256) { "Código de enrollment inválido" }
        require(name.isNotBlank()) { "Nome do dispositivo vazio" }

        val identity = identityStore.loadOrCreate()
        val existing = identity.binding
        require(existing == null || existing.serverBaseUrl == server) {
            "Identidade já vinculada a outro servidor"
        }

        val result = transport.claim(
            serverBaseUrl = server,
            enrollmentCode = code,
            deviceName = name,
            publicKey = identity.publicKey,
        )
        identityStore.bind(server, result.deviceId)
        return result
    }
}

class MemoriaDeviceTokenProvider internal constructor(
    private val identityStore: MemoriaDeviceIdentityProvider,
    private val transport: MemoriaDeviceAuthTransport,
    private val clockMillis: () -> Long,
) : DeviceTokenProvider {
    constructor(
        identityStore: MemoriaDeviceIdentityProvider,
    ) : this(identityStore, HttpMemoriaDeviceAuthTransport(), System::currentTimeMillis)

    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var expiresAtMillis: Long = 0L

    override suspend fun token(): String = mutex.withLock {
        val now = clockMillis()
        val current = cachedToken
        if (!current.isNullOrBlank() && now < expiresAtMillis) {
            return@withLock current
        }

        val binding = identityStore.loadBinding()
            ?: error("OFF.IA ainda não está vinculado a um Memoria.ia Server")
        val challenge = transport.challenge(binding.serverBaseUrl, binding.deviceId)
        val signature = identityStore.sign(challenge.signingMessage.toByteArray(Charsets.UTF_8))
        val verified = transport.verify(
            serverBaseUrl = binding.serverBaseUrl,
            deviceId = binding.deviceId,
            challengeId = challenge.challengeId,
            signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature),
        )

        cachedToken = verified.token
        // Renew at least 30 seconds before expiry and never later than half-life
        // for very short-lived test tokens.
        val refreshMarginSeconds = max(1L, minOf(30L, verified.expiresInSeconds / 2L))
        expiresAtMillis = now + (verified.expiresInSeconds - refreshMarginSeconds) * 1_000L
        verified.token
    }

    suspend fun invalidate() = mutex.withLock {
        cachedToken = null
        expiresAtMillis = 0L
    }
}
