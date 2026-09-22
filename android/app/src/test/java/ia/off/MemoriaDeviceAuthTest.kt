package ia.off

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoriaDeviceAuthTest {
    private class FakeIdentity(
        var identity: MemoriaDeviceIdentity = MemoriaDeviceIdentity(
            publicKey = "ed25519:ZmFrZS1wdWJsaWM",
            binding = null,
        ),
    ) : MemoriaDeviceIdentityProvider {
        var signedMessages = mutableListOf<ByteArray>()
        var bindCalls = mutableListOf<MemoriaDeviceBinding>()

        override fun loadOrCreate(): MemoriaDeviceIdentity = identity

        override fun bind(serverBaseUrl: String, deviceId: String): MemoriaDeviceIdentity {
            val binding = MemoriaDeviceBinding(serverBaseUrl, deviceId)
            bindCalls += binding
            identity = identity.copy(binding = binding)
            return identity
        }

        override fun loadBinding(): MemoriaDeviceBinding? = identity.binding

        override fun sign(message: ByteArray): ByteArray {
            signedMessages += message.copyOf()
            return byteArrayOf(1, 2, 3, 4)
        }
    }

    private class FakeTransport : MemoriaDeviceAuthTransport {
        var claimCalls = mutableListOf<List<String>>()
        var challengeCalls = mutableListOf<MemoriaDeviceBinding>()
        var verifyCalls = mutableListOf<List<String>>()
        var challenge = MemoriaDeviceChallenge(
            challengeId = "chl-1",
            signingMessage = "memoria-server-device-auth/v1\nserver\ndev-1\nchl-1\nnonce",
        )
        var token = MemoriaDeviceToken("token-1", 3600)

        override suspend fun claim(
            serverBaseUrl: String,
            enrollmentCode: String,
            deviceName: String,
            publicKey: String,
        ): MemoriaEnrollmentResult {
            claimCalls += listOf(serverBaseUrl, enrollmentCode, deviceName, publicKey)
            return MemoriaEnrollmentResult("dev-1", "pending_approval")
        }

        override suspend fun challenge(
            serverBaseUrl: String,
            deviceId: String,
        ): MemoriaDeviceChallenge {
            challengeCalls += MemoriaDeviceBinding(serverBaseUrl, deviceId)
            return challenge
        }

        override suspend fun verify(
            serverBaseUrl: String,
            deviceId: String,
            challengeId: String,
            signature: String,
        ): MemoriaDeviceToken {
            verifyCalls += listOf(serverBaseUrl, deviceId, challengeId, signature)
            return token
        }
    }

    @Test
    fun enrollmentUsesPublicKeyThenBindsReturnedDeviceId() = runBlocking {
        val identity = FakeIdentity()
        val transport = FakeTransport()
        val client = MemoriaDeviceEnrollmentClient(identity, transport)
        val code = "enr1_" + "a".repeat(32)

        val result = client.claim(
            serverBaseUrl = "https://memory.example/",
            enrollmentCode = code,
            deviceName = "OFF.IA teste",
        )

        assertEquals("pending_approval", result.status)
        assertEquals("dev-1", result.deviceId)
        assertEquals(
            listOf(
                "https://memory.example",
                code,
                "OFF.IA teste",
                identity.identity.publicKey,
            ),
            transport.claimCalls.single(),
        )
        assertEquals(
            MemoriaDeviceBinding("https://memory.example", "dev-1"),
            identity.bindCalls.single(),
        )
    }

    @Test
    fun enrollmentRefusesToMoveExistingIdentityToAnotherServer() {
        val identity = FakeIdentity(
            MemoriaDeviceIdentity(
                publicKey = "ed25519:ZmFrZQ",
                binding = MemoriaDeviceBinding("https://server-a.example", "dev-a"),
            ),
        )
        val transport = FakeTransport()
        val client = MemoriaDeviceEnrollmentClient(identity, transport)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                client.claim(
                    "https://server-b.example",
                    "enr1_" + "b".repeat(32),
                    "OFF.IA",
                )
            }
        }
        assertTrue(transport.claimCalls.isEmpty())
    }

    @Test
    fun tokenProviderSignsExactChallengeAndCachesShortLivedToken() = runBlocking {
        var now = 1_000_000L
        val identity = FakeIdentity(
            MemoriaDeviceIdentity(
                publicKey = "ed25519:ZmFrZQ",
                binding = MemoriaDeviceBinding("https://memory.example", "dev-1"),
            ),
        )
        val transport = FakeTransport().apply {
            token = MemoriaDeviceToken("token-abc", 120)
        }
        val provider = MemoriaDeviceTokenProvider(identity, transport) { now }

        val first = provider.token()
        val second = provider.token()

        assertEquals("token-abc", first)
        assertEquals(first, second)
        assertEquals(1, transport.challengeCalls.size)
        assertEquals(1, transport.verifyCalls.size)
        assertArrayEquals(
            transport.challenge.signingMessage.toByteArray(Charsets.UTF_8),
            identity.signedMessages.single(),
        )
        assertEquals("AQIDBA", transport.verifyCalls.single()[3])

        // expires_in=120s, refresh margin=30s => cache lifetime 90s.
        now += 89_000L
        assertEquals("token-abc", provider.token())
        assertEquals(1, transport.challengeCalls.size)

        now += 2_000L
        transport.token = MemoriaDeviceToken("token-new", 120)
        assertEquals("token-new", provider.token())
        assertEquals(2, transport.challengeCalls.size)
    }

    @Test
    fun invalidationForcesFreshChallenge() = runBlocking {
        val identity = FakeIdentity(
            MemoriaDeviceIdentity(
                publicKey = "ed25519:ZmFrZQ",
                binding = MemoriaDeviceBinding("https://memory.example", "dev-1"),
            ),
        )
        val transport = FakeTransport()
        val provider = MemoriaDeviceTokenProvider(identity, transport) { 5_000L }

        assertEquals("token-1", provider.token())
        provider.invalidate()
        transport.token = MemoriaDeviceToken("token-2", 3600)
        assertEquals("token-2", provider.token())
        assertEquals(2, transport.challengeCalls.size)
    }

    @Test
    fun tokenProviderRequiresEnrollmentBinding() {
        val provider = MemoriaDeviceTokenProvider(
            FakeIdentity(),
            FakeTransport(),
        ) { 0L }

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { provider.token() }
        }
        assertTrue(error.message.orEmpty().contains("não está vinculado"))
    }

    @Test
    fun ed25519SubjectPublicKeyInfoCodecExtractsRaw32Bytes() {
        val raw = ByteArray(32) { index -> index.toByte() }
        val prefix = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03,
            0x2b, 0x65, 0x70, 0x03, 0x21, 0x00,
        )
        val encoded = prefix + raw

        assertArrayEquals(raw, MemoriaDeviceIdentityStore.Codec.rawEd25519PublicKey(encoded))
        assertEquals(
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
            MemoriaDeviceIdentityStore.Codec.base64Url(raw),
        )
    }
}
