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

    @Test
    fun structuralHitAdaptsToExistingMemoryResolutionWithoutSemanticProjection() {
        val structural = ServerStructuralResolveResult(
            status = "HIT",
            contexts = listOf(
                ServerStructuralContext(
                    sourceText = "Meu gato se chama Alt",
                    score = 1.2,
                    exactOverlap = 2,
                    associationMass = 0.2,
                    observationIds = listOf("obs-1"),
                ),
                ServerStructuralContext(
                    sourceText = "Meu gato dorme no sofá",
                    score = 0.9,
                    exactOverlap = 1,
                    associationMass = 0.1,
                    observationIds = listOf("obs-2"),
                ),
            ),
            scannedObservations = 3,
            querySymbolCount = 5,
            semanticProjection = false,
        )

        val memory = structural.toMemoryResolution()

        assertEquals(MemoryStatus.HIT, memory.status)
        assertEquals(
            listOf("Meu gato se chama Alt", "Meu gato dorme no sofá"),
            memory.contextItems,
        )
        assertEquals(listOf("obs-1", "obs-2"), memory.memoryIds)
        assertEquals(null, memory.confidence)
        assertFalse(memory.trajectoryUsed)
    }

    @Test
    fun emptyStructuralHitFailsClosedAsUnresolved() {
        val memory = ServerStructuralResolveResult(
            status = "HIT",
            contexts = emptyList(),
            scannedObservations = 0,
            querySymbolCount = 2,
            semanticProjection = false,
        ).toMemoryResolution()

        assertEquals(MemoryStatus.UNRESOLVED, memory.status)
        assertTrue(memory.contextItems.isEmpty())
    }


    @Test
    fun laboratorySelectionUsesStructuralOnlyForRealHit() {
        val local = MemoryResolution(
            status = MemoryStatus.HIT,
            contextItems = listOf("contexto local"),
            memoryIds = listOf("local-1"),
        )
        val structuralHit = MemoryResolution(
            status = MemoryStatus.HIT,
            contextItems = listOf("contexto estrutural"),
            memoryIds = listOf("obs-1"),
        )
        val structuralMiss = MemoryResolution(
            status = MemoryStatus.UNRESOLVED,
            contextItems = emptyList(),
        )

        assertEquals(
            structuralHit,
            selectLaboratoryMemoryResolution(local, structuralHit),
        )
        assertEquals(
            local,
            selectLaboratoryMemoryResolution(local, structuralMiss),
        )
        assertEquals(
            local,
            selectLaboratoryMemoryResolution(local, null),
        )
    }

    @Test
    fun laboratorySelectionRejectsEmptyStructuralHit() {
        val local = MemoryResolution(
            status = MemoryStatus.MISS,
            contextItems = emptyList(),
        )
        val emptyHit = MemoryResolution(
            status = MemoryStatus.HIT,
            contextItems = emptyList(),
        )

        assertEquals(
            local,
            selectLaboratoryMemoryResolution(local, emptyHit),
        )
    }


    @Test
    fun turnGateAlwaysResolvesBeforeObservingCurrentUserText() = runBlocking {
        val calls = mutableListOf<String>()
        val client = object : StructuralTextMemoryClient {
            override suspend fun resolve(
                query: String,
                limit: Int,
                maxScan: Int,
            ): ServerStructuralResolveResult {
                calls += "resolve:$query"
                return ServerStructuralResolveResult(
                    status = "UNRESOLVED",
                    contexts = emptyList(),
                    scannedObservations = 0,
                    querySymbolCount = 3,
                    semanticProjection = false,
                )
            }

            override suspend fun observeUserText(
                text: String,
                sequence: Long,
                sessionId: String,
            ): ServerStructuralObserveResult {
                calls += "observe:$text:$sequence:$sessionId"
                return ServerStructuralObserveResult(
                    stored = true,
                    duplicate = false,
                    observationId = "obs-current",
                    symbolCount = 3,
                    semanticProjection = false,
                )
            }
        }

        val gate = resolveThenObserveUserText(
            client = client,
            userText = "Meu gato se chama Alt",
            sequence = 4,
            sessionId = "session-1",
        )

        assertEquals(
            listOf(
                "resolve:Meu gato se chama Alt",
                "observe:Meu gato se chama Alt:4:session-1",
            ),
            calls,
        )
        assertTrue(gate.observed)
        assertEquals(MemoryStatus.UNRESOLVED, gate.resolution?.status)
    }

    @Test
    fun turnGateStillObservesUserTextWhenResolveFails() = runBlocking {
        val calls = mutableListOf<String>()
        val client = object : StructuralTextMemoryClient {
            override suspend fun resolve(
                query: String,
                limit: Int,
                maxScan: Int,
            ): ServerStructuralResolveResult {
                calls += "resolve"
                error("synthetic resolve failure")
            }

            override suspend fun observeUserText(
                text: String,
                sequence: Long,
                sessionId: String,
            ): ServerStructuralObserveResult {
                calls += "observe"
                return ServerStructuralObserveResult(
                    stored = true,
                    duplicate = false,
                    observationId = "obs-after-failed-resolve",
                    symbolCount = 2,
                    semanticProjection = false,
                )
            }
        }

        val gate = resolveThenObserveUserText(
            client = client,
            userText = "Hoje acordei feliz",
            sequence = 2,
            sessionId = "session-2",
        )

        assertEquals(listOf("resolve", "observe"), calls)
        assertEquals(null, gate.resolution)
        assertTrue(gate.observed)
    }

}
