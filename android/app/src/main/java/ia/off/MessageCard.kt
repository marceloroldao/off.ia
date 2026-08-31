package ia.off

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun MessageCard(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    onRegenerate: ((String) -> Unit)? = null,
    onCuriosity: ((String) -> Unit)? = null,
    onImprove: ((String) -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val chatStore = remember { ChatStore(appContext) }
    val settingsStore = remember { AppSettingsStore(appContext) }
    val laboratoryMode = settingsStore.load().laboratoryMode
    val isUser = message.role == "Você"
    val responseSource = message.generation?.source
    val isLocal = responseSource == ResponseSource.LOCAL
    val isCuriosity = responseSource == ResponseSource.CURIOSITY
    val isImproved = responseSource == ResponseSource.OPENAI ||
        responseSource == ResponseSource.GEMINI ||
        responseSource == ResponseSource.MA2A
    val publicSources = message.generation?.publicSources.orEmpty()
    val publicKnowledge = message.generation?.publicKnowledge
        ?: if (isCuriosity) CuriosityPublicAuditBridge.peek(message.id) else null
    val externalPublicMemoryIdsUsed = remember(message.id, message.memory?.memoryIds) {
        chatStore.externalPublicMemoryIdsUsed(message.memory?.memoryIds.orEmpty())
    }
    val hasPublicEvidence = publicSources.isNotEmpty() || publicKnowledge != null
    var improvements by remember(message.id) { mutableStateOf(message.improvements) }
    var improving by remember(message.id) { mutableStateOf(false) }
    var improveError by remember(message.id) { mutableStateOf<String?>(null) }
    var pendingConfirmation by remember(message.id) { mutableStateOf<ImproveProviderKind?>(null) }
    var memoryExpanded by remember(message.id) { mutableStateOf(false) }
    var sourcesExpanded by remember(message.id) { mutableStateOf(false) }
    var moreExpanded by remember(message.id) { mutableStateOf(false) }

    fun executeConfiguredImprove() {
        if (!isLocal || improving || busy) return
        val settings = settingsStore.load()
        val selection = configuredImproveProvider(appContext, settings)
        val provider = selection.provider
        if (!selection.configured || provider == null) {
            improveError = selection.reason ?: "M2A2 ainda não configurada"
            return
        }

        val interaction = chatStore.findInteraction(message.id)
        if (interaction == null) {
            improveError = "Não foi possível localizar a pergunta original desta resposta"
            return
        }

        scope.launch {
            improving = true
            improveError = null
            try {
                val startedAt = System.currentTimeMillis()
                val result = provider.improve(
                    ImproveRequest(
                        userQuestion = interaction.userQuestion,
                        localAnswer = message.text,
                        selectedMemoryContext = message.memory?.selectedContext?.takeIf { it.isNotBlank() },
                    ),
                )
                val record = ImprovementRecord(
                    provider = result.provider,
                    text = result.text,
                    modelOrRoute = result.modelOrRoute,
                    latencyMs = System.currentTimeMillis() - startedAt,
                )
                improvements = (improvements + record).takeLast(5)
                if (!chatStore.appendImprovement(message.id, record)) {
                    improveError = "Resposta M2A2 recebida, mas não foi possível persistir a alternativa"
                }
            } catch (e: Exception) {
                improveError = e.message ?: e.javaClass.simpleName
            } finally {
                improving = false
            }
        }
    }

    fun requestImprove() {
        if (onImprove != null) {
            onImprove(message.id)
            return
        }
        val settings = settingsStore.load()
        val selection = configuredImproveProvider(appContext, settings)
        if (!selection.configured || selection.provider == null) {
            improveError = selection.reason ?: "M2A2 ainda não configurada"
            return
        }
        if (settings.confirmBeforeM2A2 && selection.kind == ImproveProviderKind.MA2A) {
            pendingConfirmation = ImproveProviderKind.MA2A
        } else {
            executeConfiguredImprove()
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            Text(
                text = when {
                    isUser -> "Você"
                    isCuriosity -> "OFF.IA • Curiosidade"
                    isImproved -> {
                        val provider = when (responseSource) {
                            ResponseSource.OPENAI -> "OpenAI · histórico"
                            ResponseSource.GEMINI -> "Gemini · histórico"
                            ResponseSource.MA2A -> "M2A2"
                            else -> "Externa"
                        }
                        "OFF.IA • Melhorada · $provider"
                    }
                    else -> "OFF.IA"
                },
                style = MaterialTheme.typography.labelMedium,
            )
            message.generation?.modelName?.takeIf { laboratoryMode && isImproved && it.isNotBlank() }?.let { model ->
                Text(model, style = MaterialTheme.typography.labelSmall)
            }
            Surface(
                tonalElevation = if (isUser) 1.dp else 0.dp,
                shape = MaterialTheme.shapes.large,
            ) {
                RichMessageContent(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            if (!isUser && message.text != "…") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }) {
                        Text("Copiar")
                    }
                    if (!isCuriosity || message.memory != null) {
                        TextButton(onClick = { memoryExpanded = !memoryExpanded }) {
                            Text(if (memoryExpanded) "Ocultar" else "Memória")
                        }
                    }
                    if (hasPublicEvidence) {
                        TextButton(onClick = { sourcesExpanded = !sourcesExpanded }) {
                            Text(
                                when {
                                    sourcesExpanded -> "Ocultar"
                                    publicSources.isNotEmpty() -> "Fontes (${publicSources.size})"
                                    else -> "Conhecimento"
                                },
                            )
                        }
                    }
                    if (isLocal) {
                        TextButton(
                            enabled = onCuriosity != null && !busy,
                            onClick = { onCuriosity?.invoke(message.id) },
                        ) { Text("Curiosidade") }
                    }
                    Box {
                        TextButton(onClick = { moreExpanded = true }) { Text("⋯") }
                        DropdownMenu(
                            expanded = moreExpanded,
                            onDismissRequest = { moreExpanded = false },
                        ) {
                            if (isLocal) {
                                DropdownMenuItem(
                                    enabled = !busy && !improving,
                                    text = { Text(if (improving) "Melhorando via M2A2…" else "Melhorar via M2A2") },
                                    onClick = {
                                        moreExpanded = false
                                        requestImprove()
                                    },
                                )
                                DropdownMenuItem(
                                    enabled = onRegenerate != null && !busy,
                                    text = { Text("Regenerar localmente") },
                                    onClick = {
                                        moreExpanded = false
                                        onRegenerate?.invoke(message.id)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Compartilhar resposta") },
                                onClick = {
                                    moreExpanded = false
                                    sharePlainText(
                                        context = appContext,
                                        subject = when {
                                            isCuriosity -> "Curiosidade do OFF.IA"
                                            isImproved -> "Resposta melhorada do OFF.IA"
                                            else -> "Resposta do OFF.IA"
                                        },
                                        text = message.toShareText(),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Copiar com detalhes") },
                                onClick = {
                                    moreExpanded = false
                                    clipboard.setText(AnnotatedString(message.toShareText()))
                                },
                            )
                        }
                    }
                }
            }

            improveError?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall)
            }

            if (!isUser && memoryExpanded) {
                ResponseMemoryPanel(
                    memory = message.memory,
                    externalPublicMemoryIdsUsed = externalPublicMemoryIdsUsed,
                    showDiagnostics = laboratoryMode,
                )
            }
            if (!isUser && sourcesExpanded && hasPublicEvidence) {
                PublicSourcesPanel(
                    sources = publicSources,
                    audit = publicKnowledge,
                    showDiagnostics = laboratoryMode,
                    onOpen = { source ->
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(source.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            }
            improvements.forEach { improvement ->
                ImprovementPanel(
                    improvement = improvement,
                    showDiagnostics = laboratoryMode,
                    onCopy = { clipboard.setText(AnnotatedString(improvement.text)) },
                    onShare = {
                        sharePlainText(
                            context = appContext,
                            subject = "Resposta melhorada do OFF.IA • ${improvement.provider.displayName()}",
                            text = improvement.text,
                        )
                    },
                )
            }
        }
    }

    pendingConfirmation?.let { kind ->
        AlertDialog(
            onDismissRequest = { pendingConfirmation = null },
            title = { Text("Enviar pela M2A2?") },
            text = {
                Text(
                    "OFF.IA enviará a pergunta, a resposta local e somente o contexto selecionado pela Memoria.ia para a rede M2A2. O servidor Memoria.ia será responsável pelo roteamento até o transformer.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingConfirmation = null
                    executeConfiguredImprove()
                }) { Text("Enviar pela M2A2") }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmation = null }) { Text("Cancelar") }
            },
        )
    }
}

private fun ChatMessage.toShareText(): String = buildString {
    append(text)
    val sources = generation?.publicSources.orEmpty()
    if (sources.isNotEmpty()) {
        append("\n\nFontes:\n")
        sources.forEachIndexed { index, source ->
            append("${index + 1}. ${source.title} — ${source.url}\n")
        }
    }
}.trim()

@Composable
private fun ImprovementPanel(
    improvement: ImprovementRecord,
    showDiagnostics: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "M2A2 • resposta melhorada",
                style = MaterialTheme.typography.titleSmall,
            )
            if (showDiagnostics) {
                Text("Rota: ${improvement.provider.displayName()}", style = MaterialTheme.typography.labelSmall)
                improvement.modelOrRoute?.let { route ->
                    Text(route, style = MaterialTheme.typography.labelSmall)
                }
                improvement.latencyMs?.let { latency ->
                    Text("${latency} ms", style = MaterialTheme.typography.labelSmall)
                }
            }
            RichMessageContent(text = improvement.text)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCopy) { Text("Copiar") }
                TextButton(onClick = onShare) { Text("Compartilhar") }
            }
        }
    }
}

@Composable
private fun PublicSourcesPanel(
    sources: List<CuriositySource>,
    audit: PublicKnowledgeAudit?,
    showDiagnostics: Boolean,
    onOpen: (CuriositySource) -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Fontes", style = MaterialTheme.typography.titleSmall)
            audit?.let { publicAudit ->
                Text(
                    "Memoria.ia aprendeu ${publicAudit.storedMemoryIds.size} registro(s) como conhecimento público.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (showDiagnostics) {
                    HorizontalDivider()
                    Text("Classe: ${publicAudit.knowledgeClass}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "IDs das fontes: ${if (publicAudit.sourceMemoryIds.isEmpty()) "—" else publicAudit.sourceMemoryIds.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "IDs aprendidos: ${if (publicAudit.storedMemoryIds.isEmpty()) "—" else publicAudit.storedMemoryIds.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (publicAudit.failedSourceCount > 0) {
                        Text("Fontes rejeitadas: ${publicAudit.failedSourceCount}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (publicAudit.flushFailed) {
                        Text("Falha na barreira final de persistência.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            sources.forEachIndexed { index, source ->
                if (index > 0 || audit != null) HorizontalDivider()
                Text("${index + 1}. ${source.title}", style = MaterialTheme.typography.bodyMedium)
                Text(source.domain, style = MaterialTheme.typography.labelSmall)
                source.excerpt?.takeIf { it.isNotBlank() }?.let {
                    Text(it.take(if (showDiagnostics) 320 else 160), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onOpen(source) }) { Text("Abrir fonte") }
            }
        }
    }
}

@Composable
private fun ResponseMemoryPanel(
    memory: ResponseMemoryMetadata?,
    externalPublicMemoryIdsUsed: List<String> = emptyList(),
    showDiagnostics: Boolean,
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Memoria.ia", style = MaterialTheme.typography.titleSmall)
            if (memory == null) {
                Text("Esta resposta não utilizou contexto registrado da Memoria.ia.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    "${memory.memoryIds.size} memória(s) usada(s) • ${memory.learnedMemoryIds.size} aprendida(s)",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (externalPublicMemoryIdsUsed.isNotEmpty()) {
                    Text(
                        "Conhecimento público usado: ${externalPublicMemoryIdsUsed.size} registro(s)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                memory.confidence?.let {
                    Text("Confiança: ${"%.3f".format(it)}", style = MaterialTheme.typography.bodySmall)
                }
                if (showDiagnostics) {
                    HorizontalDivider()
                    Text("Status: ${memory.status}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "IDs usados: ${if (memory.memoryIds.isEmpty()) "—" else memory.memoryIds.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "IDs aprendidos: ${if (memory.learnedMemoryIds.isEmpty()) "—" else memory.learnedMemoryIds.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (externalPublicMemoryIdsUsed.isNotEmpty()) {
                        Text(
                            "IDs públicos usados: ${externalPublicMemoryIdsUsed.joinToString()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "Trajetória: ${if (memory.trajectoryUsed) "usada" else "não usada"} • janela=${memory.conversationWindowCount}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Contexto selecionado: ${memory.selectedContext.length} caracteres", style = MaterialTheme.typography.bodySmall)
                    if (memory.selectedContext.isNotBlank()) {
                        HorizontalDivider()
                        Text(memory.selectedContext, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
