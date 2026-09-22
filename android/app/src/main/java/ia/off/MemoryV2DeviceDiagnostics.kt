package ia.off

import android.content.Context
import java.io.File

private const val V2_DIAGNOSTIC_STORAGE_ROOT = "memoria-v2-diagnostics"

/**
 * Executes the structural V2 regression catalog against the exact native mobile
 * runtime used by OFF.IA, but in an isolated BDR root so diagnostics never
 * contaminate the user's durable memory.
 */
suspend fun runStructuralV2DeviceDiagnostics(context: Context): MemoryRegressionReport {
    val appContext = context.applicationContext
    val storage = File(appContext.filesDir, V2_DIAGNOSTIC_STORAGE_ROOT)

    // A previous interrupted diagnostic must never influence the next run.
    if (storage.exists()) {
        check(storage.deleteRecursively()) {
            "Não foi possível limpar a base isolada do diagnóstico V2"
        }
    }

    var current = NativeMemoryGateway(
        context = appContext,
        storageRoot = V2_DIAGNOSTIC_STORAGE_ROOT,
        legacyFallbackEnabled = false,
    )

    return try {
        val runner = MemoryRegressionRunner(current)
        runner.run(
            scenarios = StructuralV2MemoryRegressionCatalog.scenarios,
            restartGateway = {
                current.close()
                current = NativeMemoryGateway(
                    context = appContext,
                    storageRoot = V2_DIAGNOSTIC_STORAGE_ROOT,
                )
                current
            },
        )
    } finally {
        current.close()
        if (storage.exists()) {
            storage.deleteRecursively()
        }
    }
}

fun MemoryRegressionReport.toStructuralV2DiagnosticText(): String = buildString {
    append("Memoria.ia V2 • diagnóstico nativo OFF.IA\n")
    append("PASS: $passed/${results.size}")
    if (failed > 0) append(" • FAIL: $failed")
    if (restartRequired > 0) append(" • restart pendente: $restartRequired")
    if (unavailable > 0) append(" • indisponível: $unavailable")
    append('\n')

    results.forEach { result ->
        append('\n')
        append(if (result.outcome == MemoryRegressionOutcome.PASS) "✓ " else "✗ ")
        append(result.scenario.id)
        append(" • ")
        append(result.status.name)
        append(" • ")
        append(result.latencyMs)
        append(" ms")
        if (result.selectedContext.isNotEmpty()) {
            append("\n  contexto: ")
            append(result.selectedContext.joinToString(" | "))
        }
        if (result.missingTerms.isNotEmpty()) {
            append("\n  faltando: ")
            append(result.missingTerms.joinToString())
        }
        if (result.forbiddenTermsFound.isNotEmpty()) {
            append("\n  indevido: ")
            append(result.forbiddenTermsFound.joinToString())
        }
    }
}
