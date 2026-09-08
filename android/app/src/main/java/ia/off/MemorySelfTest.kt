package ia.off

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs the canonical memory regression suite in disposable Memoria.ia stores.
 * The user's durable `memoria-v2` database is never opened by this service.
 */
object MemorySelfTest {
    private const val ROOT_PREFIX = "memoria-selftest"

    suspend fun run(context: Context): MemoryRegressionReport = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val allResults = mutableListOf<MemoryRegressionResult>()

        MemoryRegressionCatalog.scenarios.forEachIndexed { index, scenario ->
            val root = "$ROOT_PREFIX-${scenario.id}-$index"
            val rootFile = File(appContext.filesDir, root)
            rootFile.deleteRecursively()

            var gateway: NativeMemoryGateway? = null
            try {
                gateway = NativeMemoryGateway(appContext, root)
                val runner = MemoryRegressionRunner(gateway)
                val report = runner.run(
                    scenarios = listOf(scenario),
                    restartGateway = if (scenario.requiresRestart) {
                        suspend {
                            gateway?.close()
                            gateway = null
                            NativeMemoryGateway(appContext, root).also { gateway = it }
                        }
                    } else null,
                )
                allResults += report.results
            } catch (_: Throwable) {
                allResults += MemoryRegressionResult(
                    scenario = scenario,
                    outcome = MemoryRegressionOutcome.UNAVAILABLE,
                    status = MemoryStatus.UNAVAILABLE,
                    selectedContext = emptyList(),
                    missingTerms = scenario.expectedTerms,
                    forbiddenTermsFound = emptyList(),
                    latencyMs = 0,
                )
            } finally {
                runCatching { gateway?.close() }
                rootFile.deleteRecursively()
            }
        }
        MemoryRegressionReport(allResults)
    }

    fun formatShortReport(report: MemoryRegressionReport): String = buildString {
        appendLine("Memoria.ia — autoteste")
        appendLine("Total: ${report.results.size} • acertos: ${report.passed} • falhas: ${report.failed}")
        if (report.unavailable > 0) appendLine("Indisponíveis: ${report.unavailable}")
        if (report.restartRequired > 0) appendLine("Reinício pendente: ${report.restartRequired}")
        report.byCategory().forEach { (category, score) ->
            val (passed, total) = score
            if (total > 0) appendLine("${category.label()}: $passed/$total")
        }
        append("OFF.IA ${BuildConfig.VERSION_NAME} • Memoria.ia ${BuildConfig.MEMORIA_IA_VERSION} • BDR ${BuildConfig.BDR_VERSION}")
    }

    private fun MemoryRegressionCategory.label(): String = when (this) {
        MemoryRegressionCategory.FACT -> "Fatos"
        MemoryRegressionCategory.CORRECTION -> "Correções"
        MemoryRegressionCategory.COLLECTION -> "Coleções"
        MemoryRegressionCategory.RESTART -> "Reinício"
        MemoryRegressionCategory.AUTHORITY -> "Autoridade"
        MemoryRegressionCategory.CONTAMINATION -> "Contaminação"
        MemoryRegressionCategory.CONTEXT -> "Contexto"
    }
}
