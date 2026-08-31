from pathlib import Path

# One-shot branch patcher; workflow removes this file after applying the change.
p = Path('android/app/src/main/java/ia/off/MainActivity.kt')
s = p.read_text(encoding='utf-8')
old = '''                status = "Online • registrando fontes públicas na Memoria.ia…"
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
'''
new = '''                status = "Online • registrando fontes públicas na Memoria.ia…"
                val publicContext = learnAndResolveCuriosity(
                    memory = memory,
                    result = result,
                    userQuestion = userQuestion,
                    sessionId = activeSessionId,
                    requestId = curiosityMessage.id,
                )
                val publicLearning = publicContext.learning
                saveWorkspace()

                if (!publicContext.readyForRendering) {
                    val detail = publicLearning.failureReason?.take(180)
                    messages[curiosityIndex] = messages[curiosityIndex].copy(
                        text = when {
                            publicLearning.flushFailed ->
                                "Curiosidade encontrou fontes, mas a Memoria.ia não confirmou a persistência pública. O modelo local não foi chamado."
                            !detail.isNullOrBlank() ->
                                "Curiosidade encontrou fontes, mas a Memoria.ia rejeitou evidência pública: $detail"
                            publicLearning.learned ->
                                "As fontes foram memorizadas, mas a Memoria.ia não selecionou contexto público confiável para esta pergunta. O modelo local não foi chamado."
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
                        publicLearning.learned ->
                            "Offline • Curiosidade interrompida • contexto público não resolvido"
                        else ->
                            "Online • Curiosidade interrompida • memória pública incompleta"
                    }
                    return@launch
                }

                status = "Offline • Memoria.ia resolveu contexto público • gerando resposta final…"
                generating = true
                val generationStartedAt = System.currentTimeMillis()
                val prompt = materializeResolvedCuriosityPrompt(userQuestion, publicContext.resolution)
'''
if old not in s:
    raise SystemExit('target block not found')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
