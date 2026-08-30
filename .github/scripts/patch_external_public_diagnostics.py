from pathlib import Path

repls = {
    "android/app/src/main/cpp/offia_memory_jni.cpp": [
        (
            '''        if (status != MEMORIA_MOBILE_OK) {\n            throw_illegal_state(env, "Memoria.ia rejeitou conhecimento público externo");\n            return nullptr;\n        }''',
            '''        if (status != MEMORIA_MOBILE_OK) {\n            std::string detail = "Memoria.ia external_public status=" +\n                std::to_string(static_cast<int>(status));\n            if (!response.empty()) {\n                detail += " response=" + response.substr(0, 512);\n            }\n            throw_illegal_state(env, detail);\n            return nullptr;\n        }''',
        ),
    ],
    "android/app/src/main/java/ia/off/CuriosityMemoryLearning.kt": [
        (
            '''    val synthesisStored: Boolean = false,\n    val flushFailed: Boolean = false,\n) {''',
            '''    val synthesisStored: Boolean = false,\n    val flushFailed: Boolean = false,\n    val failureReason: String? = null,\n) {''',
        ),
        (
            '''    var primaryLearnedSource: CuriositySource? = null\n    var failedSources = 0\n''',
            '''    var primaryLearnedSource: CuriositySource? = null\n    var failedSources = 0\n    var firstFailureReason: String? = null\n''',
        ),
        (
            '''        } catch (_: Exception) {\n            failedSources += 1\n        }''',
            '''        } catch (e: Exception) {\n            failedSources += 1\n            if (firstFailureReason == null) {\n                firstFailureReason = e.message ?: e.javaClass.simpleName\n            }\n        }''',
        ),
        (
            '''        } catch (_: Exception) {\n            // Imported public sources remain valid even if Memoria.ia rejects\n            // a derived synthesis conservatively.\n        }''',
            '''        } catch (e: Exception) {\n            if (firstFailureReason == null) {\n                firstFailureReason = e.message ?: e.javaClass.simpleName\n            }\n            // Imported public sources remain valid even if Memoria.ia rejects\n            // a derived synthesis conservatively.\n        }''',
        ),
        (
            '''        synthesisStored = synthesisStored,\n        flushFailed = flushFailed,\n    )''',
            '''        synthesisStored = synthesisStored,\n        flushFailed = flushFailed,\n        failureReason = firstFailureReason,\n    )''',
        ),
    ],
    "android/app/src/main/java/ia/off/MainActivity.kt": [
        (
            '''                    publicLearning.failedSourceCount > 0 ->\n                        "Offline • Curiosidade concluída • aprendizado público indisponível"''',
            '''                    publicLearning.failedSourceCount > 0 -> {\n                        val detail = publicLearning.failureReason?.take(180)\n                        if (detail.isNullOrBlank()) {\n                            "Offline • Curiosidade concluída • aprendizado público indisponível"\n                        } else {\n                            "Offline • Curiosidade • $detail"\n                        }\n                    }''',
        ),
    ],
    "android/app/src/test/java/ia/off/CuriosityMemoryLearningTest.kt": [
        (
            '''    @Test\n    fun flushFailureDoesNotDiscardLearnedPublicKnowledgeReport() = runBlocking {''',
            '''    @Test\n    fun sourceFailurePreservesNativeDiagnosticReason() = runBlocking {\n        CuriosityPublicAuditBridge.clearForTests()\n        val memory = RecordingMemoryGateway(failExternal = true)\n        val result = CuriosityResult(\n            sourceText = "public material",\n            sources = listOf(\n                CuriositySource(\n                    title = "Source A",\n                    url = "https://pt.wikipedia.org/wiki/A",\n                    domain = "pt.wikipedia.org",\n                    excerpt = "A public fact is 7319.",\n                ),\n            ),\n        )\n\n        val report = learnCuriosityResult(\n            memory = memory,\n            result = result,\n            synthesis = "",\n            sessionId = "session-error",\n            requestId = "curiosity-error",\n            acquiredTime = "2026-08-30T12:00:50Z",\n        )\n\n        assertFalse(report.learned)\n        assertEquals(1, report.failedSourceCount)\n        assertTrue(report.failureReason?.contains("status=2") == true)\n    }\n\n    @Test\n    fun flushFailureDoesNotDiscardLearnedPublicKnowledgeReport() = runBlocking {''',
        ),
        (
            '''private class RecordingMemoryGateway(\n    private val failFlush: Boolean = false,\n) : MemoryGateway {''',
            '''private class RecordingMemoryGateway(\n    private val failFlush: Boolean = false,\n    private val failExternal: Boolean = false,\n) : MemoryGateway {''',
        ),
        (
            '''    override suspend fun learnExternalKnowledge(source: ExternalKnowledgeSource): ExternalKnowledgeLearnResult {\n        requests += source\n        val id = "memory-${requests.size}"''',
            '''    override suspend fun learnExternalKnowledge(source: ExternalKnowledgeSource): ExternalKnowledgeLearnResult {\n        requests += source\n        if (failExternal) error("Memoria.ia external_public status=2 response={\\\"status\\\":\\\"INVALID_ARGUMENT\\\"}")\n        val id = "memory-${requests.size}"''',
        ),
    ],
}

for path, pairs in repls.items():
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    for old, new in pairs:
        count = text.count(old)
        if count != 1:
            raise SystemExit(f"anchor mismatch {path}: expected 1, got {count}: {old[:80]!r}")
        text = text.replace(old, new, 1)
    p.write_text(text, encoding="utf-8")

print("patched", ", ".join(repls))
