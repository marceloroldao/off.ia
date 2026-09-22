package ia.off

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRegressionRunnerTest {
    @Test
    fun evaluatesExpectedAndForbiddenTermsWithoutSemanticFallback() = runBlocking {
        val gateway = FakeGateway(mapOf("Como se chama meu gato?" to listOf("Meu gato se chama Alt.")))
        val scenario = MemoryRegressionScenario(
            "fact", MemoryRegressionCategory.FACT, listOf("Meu gato se chama Alt."),
            "Como se chama meu gato?", listOf("Alt"), listOf("Nino"),
        )
        val report = MemoryRegressionRunner(gateway).run(listOf(scenario))
        assertEquals(1, report.passed)
        assertEquals(0, report.failed)
        assertEquals(listOf("Meu gato se chama Alt."), report.results.single().selectedContext)
    }

    @Test
    fun reportsObsoleteCorrectionAsFailure() = runBlocking {
        val gateway = FakeGateway(mapOf("Qual é a cor?" to listOf("O Corsa é branco.")))
        val scenario = MemoryRegressionScenario(
            "correction", MemoryRegressionCategory.CORRECTION,
            listOf("O Corsa é branco.", "Correção: o Corsa é prata."),
            "Qual é a cor?", listOf("prata"), listOf("branco"),
        )
        val result = MemoryRegressionRunner(gateway).run(listOf(scenario)).results.single()
        assertEquals(MemoryRegressionOutcome.FAIL, result.outcome)
        assertEquals(listOf("prata"), result.missingTerms)
        assertEquals(listOf("branco"), result.forbiddenTermsFound)
    }

    @Test
    fun structuralV2RequiresPreferredAttractorWithoutDeletingOlderEvidence() = runBlocking {
        val gateway = FakeGateway(
            mapOf(
                "Como se chama meu gato?" to listOf(
                    "Meu gato se chama Alt2.",
                    "Meu gato se chama Alt.",
                ),
            ),
        )
        val scenario = StructuralV2MemoryRegressionCatalog.scenarios
            .first { it.id == "v2-conflict-cat-recurrence" }

        val result = MemoryRegressionRunner(gateway).run(listOf(scenario)).results.single()

        assertEquals(MemoryRegressionOutcome.PASS, result.outcome)
        assertTrue(result.selectedContext.joinToString("\n").contains("Alt"))
        assertTrue(result.selectedContext.joinToString("\n").contains("Alt2"))
    }

    @Test
    fun structuralV2FailsWhenOldObservationWinsTheFirstAttractor() = runBlocking {
        val gateway = FakeGateway(
            mapOf(
                "Como se chama meu gato?" to listOf(
                    "Meu gato se chama Alt.",
                    "Meu gato se chama Alt2.",
                ),
            ),
        )
        val scenario = StructuralV2MemoryRegressionCatalog.scenarios
            .first { it.id == "v2-conflict-cat-recurrence" }

        val result = MemoryRegressionRunner(gateway).run(listOf(scenario)).results.single()

        assertEquals(MemoryRegressionOutcome.FAIL, result.outcome)
    }

    @Test
    fun restartScenarioIsNeverPretendedWhenNoRestartHookExists() = runBlocking {
        val gateway = FakeGateway(emptyMap())
        val scenario = MemoryRegressionCatalog.scenarios.first { it.requiresRestart }
        val result = MemoryRegressionRunner(gateway).run(listOf(scenario)).results.single()
        assertEquals(MemoryRegressionOutcome.RESTART_REQUIRED, result.outcome)
    }

    @Test
    fun unavailableGatewayMarksWholeRunUnavailable() = runBlocking {
        val report = MemoryRegressionRunner(UnavailableMemoryGateway).run(MemoryRegressionCatalog.scenarios.take(2))
        assertEquals(2, report.unavailable)
        assertTrue(report.results.all { it.outcome == MemoryRegressionOutcome.UNAVAILABLE })
    }

    private class FakeGateway(private val answers: Map<String, List<String>>) : MemoryGateway {
        override val available = true
        val learned = mutableListOf<String>()

        override suspend fun resolve(message: String, sessionId: String?, conversationWindow: List<MemoryWindowTurn>) =
            MemoryResolution(MemoryStatus.HIT, contextItems = answers[message].orEmpty())

        override suspend fun learnTurn(userText: String, assistantText: String): MemoryLearnResult {
            learned += userText
            return MemoryLearnResult()
        }

        override suspend fun learnExternalKnowledge(source: ExternalKnowledgeSource) = ExternalKnowledgeLearnResult()
        override suspend fun exportSnapshotPage(turnOffset: Int, episodeOffset: Int, limit: Int): String? = null
        override suspend fun flush() = Unit
    }
}
