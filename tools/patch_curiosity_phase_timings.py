from pathlib import Path

p = Path('android/app/src/main/java/ia/off/MainActivity.kt')
s = p.read_text(encoding='utf-8')

old1 = '''        generationJob = scope.launch {
            busy = true
            status = "Online • buscando fontes públicas…"
            val answer = StringBuilder()
            try {
                val result = curiosityProvider.acquire(
'''
new1 = '''        generationJob = scope.launch {
            busy = true
            val curiosityStartedAt = System.nanoTime()
            status = "Online • buscando fontes públicas…"
            val answer = StringBuilder()
            try {
                val acquisitionStartedAt = System.nanoTime()
                val result = curiosityProvider.acquire(
'''
if old1 not in s:
    raise SystemExit('start block not found')
s = s.replace(old1, new1, 1)

old2 = '''                    ),
                )
                messages[curiosityIndex] = messages[curiosityIndex].copy(
'''
new2 = '''                    ),
                )
                val acquisitionLatencyMs = (System.nanoTime() - acquisitionStartedAt) / 1_000_000L
                messages[curiosityIndex] = messages[curiosityIndex].copy(
'''
# only replace the occurrence immediately inside Curiosity region after the marker
idx = s.find('val acquisitionStartedAt = System.nanoTime()')
pos = s.find(old2, idx)
if pos < 0:
    raise SystemExit('acquisition end block not found')
s = s[:pos] + s[pos:].replace(old2, new2, 1)

old3 = '''                val generationStartedAt = System.currentTimeMillis()
                val prompt = materializeResolvedCuriosityPrompt(userQuestion, publicContext.resolution)
'''
new3 = '''                val generationStartedAt = System.nanoTime()
                val prompt = materializeResolvedCuriosityPrompt(userQuestion, publicContext.resolution)
'''
if old3 not in s:
    raise SystemExit('generation start block not found')
s = s.replace(old3, new3, 1)

old4 = '''                val generationLatency = System.currentTimeMillis() - generationStartedAt
                messages[curiosityIndex] = messages[curiosityIndex].copy(
'''
new4 = '''                val generationLatency = (System.nanoTime() - generationStartedAt) / 1_000_000L
                val totalLatencyMs = (System.nanoTime() - curiosityStartedAt) / 1_000_000L
                messages[curiosityIndex] = messages[curiosityIndex].copy(
'''
if old4 not in s:
    raise SystemExit('generation end block not found')
s = s.replace(old4, new4, 1)

old5 = '''                status = "Offline • Curiosidade concluída • ${result.sources.size} fonte(s) memorizada(s) • ${publicLearning.storedMemoryIds.size} memória(s) pública(s)"
'''
new5 = '''                status = "Offline • Curiosidade • web ${formatCuriosityLatencyMs(acquisitionLatencyMs)} • memória ${formatCuriosityLatencyMs(publicContext.persistenceLatencyMs)} • resolve ${formatCuriosityLatencyMs(publicContext.resolutionLatencyMs)} • LLM ${formatCuriosityLatencyMs(generationLatency)} • total ${formatCuriosityLatencyMs(totalLatencyMs)}"
'''
if old5 not in s:
    raise SystemExit('final status block not found')
s = s.replace(old5, new5, 1)

p.write_text(s, encoding='utf-8')
