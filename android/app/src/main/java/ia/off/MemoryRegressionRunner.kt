package ia.off

import kotlin.system.measureTimeMillis

enum class MemoryRegressionOutcome { PASS, FAIL, RESTART_REQUIRED, UNAVAILABLE }

data class MemoryRegressionResult(
    val scenario: MemoryRegressionScenario,
    val outcome: MemoryRegressionOutcome,
    val status: MemoryStatus,
    val selectedContext: List<String>,
    val missingTerms: List<String>,
    val forbiddenTermsFound: List<String>,
    val latencyMs: Long,
)

data class MemoryRegressionReport(val results: List<MemoryRegressionResult>) {
    val passed: Int get() = results.count { it.outcome == MemoryRegressionOutcome.PASS }
    val failed: Int get() = results.count { it.outcome == MemoryRegressionOutcome.FAIL }
    val restartRequired: Int get() = results.count { it.outcome == MemoryRegressionOutcome.RESTART_REQUIRED }
    val unavailable: Int get() = results.count { it.outcome == MemoryRegressionOutcome.UNAVAILABLE }

    fun byCategory(): Map<MemoryRegressionCategory, Pair<Int, Int>> =
        MemoryRegressionCategory.entries.associateWith { category ->
            val completed = results.filter { it.scenario.category == category && it.outcome in setOf(MemoryRegressionOutcome.PASS, MemoryRegressionOutcome.FAIL) }
            completed.count { it.outcome == MemoryRegressionOutcome.PASS } to completed.size
        }
}

/**
 * Thin diagnostic orchestrator. It never ranks, stores or resolves memory itself:
 * all semantic behavior remains owned by the supplied Memoria.ia MemoryGateway.
 */
class MemoryRegressionRunner(private val gateway: MemoryGateway) {
    suspend fun run(
        scenarios: List<MemoryRegressionScenario> = MemoryRegressionCatalog.scenarios,
        restartGateway: (suspend () -> MemoryGateway)? = null,
    ): MemoryRegressionReport {
        if (!gateway.available) {
            return MemoryRegressionReport(scenarios.map { unavailable(it) })
        }

        val results = mutableListOf<MemoryRegressionResult>()
        for (scenario in scenarios) {
            var activeGateway = gateway
            val sessionId = "memory-regression:${scenario.id}"
            scenario.setupTurns.forEachIndexed { index, turn ->
                activeGateway.observeUser(
                    text = turn,
                    sessionId = sessionId,
                    sourceId = "memory-regression:${scenario.id}:setup:$index",
                    sequence = index.toLong(),
                )
            }
            activeGateway.flush()

            if (scenario.requiresRestart) {
                val factory = restartGateway
                if (factory == null) {
                    results += restartRequired(scenario)
                    continue
                }
                activeGateway = factory()
                if (!activeGateway.available) {
                    results += unavailable(scenario)
                    continue
                }
            }

            var resolution = MemoryResolution(MemoryStatus.UNRESOLVED)
            val elapsed = measureTimeMillis {
                resolution = activeGateway.resolve(scenario.query, sessionId = sessionId)
            }
            results += evaluate(scenario, resolution, elapsed)
        }
        return MemoryRegressionReport(results)
    }

    private fun evaluate(
        scenario: MemoryRegressionScenario,
        resolution: MemoryResolution,
        latencyMs: Long,
    ): MemoryRegressionResult {
        val searchable = resolution.contextItems.joinToString("\n")
        val firstContext = resolution.contextItems.firstOrNull().orEmpty()
        val missing = scenario.expectedTerms.filterNot { searchable.contains(it, ignoreCase = true) }
        val missingPreferred = scenario.preferredFirstContextTerms.filterNot {
            firstContext.contains(it, ignoreCase = true)
        }
        val forbidden = scenario.forbiddenTerms.filter { searchable.contains(it, ignoreCase = true) }
        val wrongStatus = scenario.expectedStatus?.let { resolution.status != it } ?: false
        val outcome = when {
            resolution.status == MemoryStatus.UNAVAILABLE -> MemoryRegressionOutcome.UNAVAILABLE
            !wrongStatus && missing.isEmpty() && missingPreferred.isEmpty() && forbidden.isEmpty() ->
                MemoryRegressionOutcome.PASS
            else -> MemoryRegressionOutcome.FAIL
        }
        return MemoryRegressionResult(scenario, outcome, resolution.status, resolution.contextItems, missing, forbidden, latencyMs)
    }

    private fun restartRequired(scenario: MemoryRegressionScenario) = MemoryRegressionResult(
        scenario, MemoryRegressionOutcome.RESTART_REQUIRED, MemoryStatus.UNRESOLVED, emptyList(), emptyList(), emptyList(), 0,
    )

    private fun unavailable(scenario: MemoryRegressionScenario) = MemoryRegressionResult(
        scenario, MemoryRegressionOutcome.UNAVAILABLE, MemoryStatus.UNAVAILABLE, emptyList(), scenario.expectedTerms, emptyList(), 0,
    )
}
