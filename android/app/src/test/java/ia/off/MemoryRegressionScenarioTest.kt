package ia.off

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRegressionScenarioTest {
    @Test
    fun catalogHasAtLeastTwentyDeterministicScenarios() {
        val scenarios = MemoryRegressionCatalog.scenarios
        assertTrue(scenarios.size >= 20)
        assertEquals(scenarios.size, scenarios.map { it.id }.toSet().size)
        assertTrue(scenarios.all { it.id.isNotBlank() && it.query.isNotBlank() })
        assertTrue(scenarios.all { it.setupTurns.isNotEmpty() })
    }

    @Test
    fun catalogCoversEveryRequiredRegressionCategory() {
        val categories = MemoryRegressionCatalog.scenarios.map { it.category }.toSet()
        assertEquals(MemoryRegressionCategory.entries.toSet(), categories)
    }

    @Test
    fun restartScenariosAreExplicitlyMarked() {
        val restartScenarios = MemoryRegressionCatalog.scenarios
            .filter { it.category == MemoryRegressionCategory.RESTART }
        assertTrue(restartScenarios.isNotEmpty())
        assertTrue(restartScenarios.all { it.requiresRestart })
    }

    @Test
    fun correctionScenariosDefineObsoleteTermsToReject() {
        val corrections = MemoryRegressionCatalog.scenarios
            .filter { it.category == MemoryRegressionCategory.CORRECTION }
        assertTrue(corrections.isNotEmpty())
        assertTrue(corrections.all { it.expectedTerms.isNotEmpty() && it.forbiddenTerms.isNotEmpty() })
    }

    @Test
    fun structuralV2CatalogKeepsConflictingEvidenceInsteadOfDeletingIt() {
        val scenarios = StructuralV2MemoryRegressionCatalog.scenarios
        assertTrue(scenarios.isNotEmpty())
        assertEquals(scenarios.size, scenarios.map { it.id }.toSet().size)

        val conflict = scenarios.first { it.id == "v2-conflict-cat-recurrence" }
        assertTrue(conflict.expectedTerms.containsAll(listOf("Alt", "Alt2")))
        assertTrue(conflict.forbiddenTerms.isEmpty())
        assertEquals(listOf("Alt2"), conflict.preferredFirstContextTerms)
        assertEquals(MemoryStatus.HIT, conflict.expectedStatus)
    }

    @Test
    fun structuralV2CatalogCoversRestartAndUnrelatedQuery() {
        val scenarios = StructuralV2MemoryRegressionCatalog.scenarios
        assertTrue(scenarios.any { it.requiresRestart })
        assertTrue(
            scenarios.any {
                it.expectedStatus == MemoryStatus.UNRESOLVED &&
                    it.category == MemoryRegressionCategory.CONTAMINATION
            },
        )
    }
}
