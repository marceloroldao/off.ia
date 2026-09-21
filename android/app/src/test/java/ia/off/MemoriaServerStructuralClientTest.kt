package ia.off

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoriaServerStructuralClientTest {
    private class FakeTransport : StructuralMemoryTransport {
        var observeBaseUrl: String? = null
        var observeToken: String? = null
        var observeRequest: StructuralObserveRequest? = null
        var resolveBaseUrl: String? = null
        var resolveToken: String? = null
        var resolveRequest: StructuralResolveRequest? = null
        var observeResult = ServerStructuralObserveResult(
            stored = true,
            duplicate = false,
            observationId = "obs-1",
            symbolCount = 5,
            semanticProjection = false,
        )
        var resolveResult = ServerStructuralResolveResult(
            status = "HIT",
            contexts = listOf(
                ServerStructuralContext(
                    sourceText = "Meu gato se chama Alt",
                    score = 1.25,
                    exactOverlap = 2,
                    associationMass = 0.25,
                    observationIds = listOf("obs-1"),
                ),
            ),
            scannedObservations = 4,
            querySymbolCount = 6,
            semanticProjection = false,
        )

        override suspend fun observe(
            baseUrl: String,
            deviceToken: String,
            request: StructuralObserveRequest,
        ): ServerStructuralObserveResult {
            observeBaseUrl = baseUrl
            observeToken = deviceToken
            observeRequest = request
            return observeResult
        }

        override suspend fun resolve(
            baseUrl: String,
            deviceToken: String,
            request: StructuralResolveRequest,
        ): ServerStructuralResolveResult {
            resolveBaseUrl = baseUrl
            resolveToken = deviceToken
            resolveRequest = request
            return resolveResult
        }
    }

    @Test
    fun observeUsesOnlyDeviceTokenAndUserPayload() = runBlocking {
        val transport = FakeTransport()
        val client = MemoriaServerStructuralClient(
            serverBaseUrl = "https://memory.example/",
            tokenProvider = DeviceTokenProvider { " token-123 " },
            transport = transport,
        )

        val result = client.observeUserText(
            text = "  Meu gato se chama Alt  ",
            sequence = 9,
            sessionId = "chat-1",
        )

        assertTrue(result.stored)
        assertFalse(result.semanticProjection)
        assertEquals("https://memory.example", transport.observeBaseUrl)
        assertEquals("token-123", transport.observeToken)
        assertEquals(
            StructuralObserveRequest(
                text = "Meu gato se chama Alt",
                sequence = 9,
                sessionId = "chat-1",
            ),
            transport.observeRequest,
        )
    }

    @Test
    fun resolveKeepsQueryReadOnlyAtClientContract() = runBlocking {
        val transport = FakeTransport()
        val client = MemoriaServerStructuralClient(
            serverBaseUrl = "http://10.0.0.2:8780",
            tokenProvider = DeviceTokenProvider { "device-token" },
            transport = transport,
        )

        val result = client.resolve(
            query = " qual o nome do meu gato? ",
            limit = 2,
            maxScan = 4096,
        )

        assertEquals("HIT", result.status)
        assertEquals("Meu gato se chama Alt", result.contexts.single().sourceText)
        assertEquals(
            StructuralResolveRequest(
                query = "qual o nome do meu gato?",
                limit = 2,
                maxScan = 4096,
            ),
            transport.resolveRequest,
        )
        assertEquals("device-token", transport.resolveToken)
        assertEquals("http://10.0.0.2:8780", transport.resolveBaseUrl)
    }

    @Test
    fun blankDeviceTokenFailsBeforeTransport() {
        val transport = FakeTransport()
        val client = MemoriaServerStructuralClient(
            serverBaseUrl = "https://memory.example",
            tokenProvider = DeviceTokenProvider { "   " },
            transport = transport,
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                client.resolve("gato")
            }
        }
        assertEquals(null, transport.resolveRequest)
    }

    @Test
    fun semanticProjectionFailsClosed() {
        val transport = FakeTransport().apply {
            resolveResult = resolveResult.copy(semanticProjection = true)
        }
        val client = MemoriaServerStructuralClient(
            serverBaseUrl = "https://memory.example",
            tokenProvider = DeviceTokenProvider { "token" },
            transport = transport,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                client.resolve("gato")
            }
        }
        assertTrue(error.message.orEmpty().contains("projeção semântica"))
    }

    @Test
    fun validatesServerAndRequestBoundsBeforeNetwork() {
        assertThrows(IllegalArgumentException::class.java) {
            MemoriaServerStructuralClient(
                serverBaseUrl = "ftp://memory.example",
                tokenProvider = DeviceTokenProvider { "token" },
                transport = FakeTransport(),
            )
        }

        val transport = FakeTransport()
        val client = MemoriaServerStructuralClient(
            serverBaseUrl = "https://memory.example",
            tokenProvider = DeviceTokenProvider { "token" },
            transport = transport,
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { client.observeUserText("texto", -1, "session") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { client.resolve("query", limit = 21) }
        }
        assertEquals(null, transport.observeRequest)
        assertEquals(null, transport.resolveRequest)
    }
}
