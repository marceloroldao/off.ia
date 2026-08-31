from pathlib import Path

path = Path("android/app/src/main/java/ia/off/MainActivity.kt")
text = path.read_text(encoding="utf-8")
start_marker = '                status = "Offline • sintetizando Curiosidade localmente…"\n'
end_marker = '            } catch (_: CancellationException) {'

if text.count(start_marker) != 1:
    raise SystemExit(f"expected exactly one start marker, got {text.count(start_marker)}")
start = text.index(start_marker)
end = text.index(end_marker, start)

replacement = '''                status = "Online • registrando fontes públicas na Memoria.ia…"
                val publicLearning = learnCuriositySources(
                    memory = memory,
                    result = result,
                    sessionId = activeSessionId,
                    requestId = curiosityMessage.id,
                )
                saveWorkspace()

                if (!publicLearning.learned || publicLearning.failedSourceCount > 0 || publicLearning.flushFailed) {
                    val detail = publicLearning.failureReason?.take(180)
                    messages[curiosityIndex] = messages[curiosityIndex].copy(
                        text = when {
                            publicLearning.flushFailed ->
                                "Curiosidade encontrou fontes, mas a Memoria.ia não confirmou a persistência pública. O modelo local não foi chamado."
                            !detail.isNullOrBlank() ->
                                "Curiosidade encontrou fontes, mas a Memoria.ia rejeitou evidência pública: $detail"
                            else ->
                                "Curiosidade encontrou fontes, mas nem todas foram aceitas pela Memoria.ia. O modelo local não foi chamado."
                        },
                    )
                    saveWorkspace()
                    status = when {
                        publicLearning.flushFailed ->
                            "Online • Curiosidade interrompida • flush da memória pública falhou"
                        !detail.isNullOrBlank() ->
                            "Online • Curiosidade • $detail"
                        else ->
                            "Online • Curiosidade interrompida • memória pública incompleta"
                    }
                    return@launch
                }

                status = "Offline • fontes públicas memorizadas • sintetizando resposta final…"
                generating = true
                val generationStartedAt = System.currentTimeMillis()
                val prompt = materializeCuriosityPrompt(userQuestion, localResponse.text, result)
                engine.sendUserPrompt(prompt, predictLength = 512).collect { token ->
                    answer.append(token)
                    messages[curiosityIndex] = messages[curiosityIndex].copy(text = answer.toString())
                }
                val generationLatency = System.currentTimeMillis() - generationStartedAt
                messages[curiosityIndex] = messages[curiosityIndex].copy(
                    text = answer.toString().ifBlank { "As fontes foram memorizadas, mas o modelo não gerou uma resposta final." },
                    generation = messages[curiosityIndex].generation?.copy(
                        latencyMs = generationLatency,
                        publicSources = result.sources,
                    ),
                )
                saveWorkspace()
                status = "Offline • Curiosidade concluída • ${result.sources.size} fonte(s) memorizada(s) • ${publicLearning.storedMemoryIds.size} memória(s) pública(s)"
'''

path.write_text(text[:start] + replacement + text[end:], encoding="utf-8")
