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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

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
    val isUser = message.role == "Você"
    val isCuriosity = message.generation?.source == ResponseSource.CURIOSITY
    val publicSources = message.generation?.publicSources.orEmpty()
    var memoryExpanded by remember(message.id) { mutableStateOf(false) }
    var sourcesExpanded by remember(message.id) { mutableStateOf(false) }
    var moreExpanded by remember(message.id) { mutableStateOf(false) }

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
                    else -> "OFF.IA"
                },
                style = MaterialTheme.typography.labelMedium,
            )
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
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }) {
                        Text("Copiar")
                    }
                    TextButton(onClick = { memoryExpanded = !memoryExpanded }) {
                        Text(if (memoryExpanded) "Ocultar memória" else "Memória")
                    }
                    if (publicSources.isNotEmpty()) {
                        TextButton(onClick = { sourcesExpanded = !sourcesExpanded }) {
                            Text(if (sourcesExpanded) "Ocultar fontes" else "Fontes (${publicSources.size})")
                        }
                    }
                    TextButton(
                        enabled = onCuriosity != null && !busy && !isCuriosity,
                        onClick = { onCuriosity?.invoke(message.id) },
                    ) { Text("Curiosidade") }
                    TextButton(
                        enabled = onImprove != null && !busy,
                        onClick = { onImprove?.invoke(message.id) },
                    ) { Text("Melhorar") }
                    TextButton(
                        enabled = onRegenerate != null && !busy && !isCuriosity,
                        onClick = { onRegenerate?.invoke(message.id) },
                    ) { Text("Regenerar") }
                    Box {
                        TextButton(onClick = { moreExpanded = true }) { Text("⋯") }
                        DropdownMenu(
                            expanded = moreExpanded,
                            onDismissRequest = { moreExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Compartilhar resposta") },
                                onClick = {
                                    moreExpanded = false
                                    sharePlainText(
                                        context = context.applicationContext,
                                        subject = if (isCuriosity) "Curiosidade do OFF.IA" else "Resposta do OFF.IA",
                                        text = message.toShareText(),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Copiar resposta") },
                                onClick = {
                                    moreExpanded = false
                                    clipboard.setText(AnnotatedString(message.toShareText()))
                                },
                            )
                        }
                    }
                }
            }

            if (!isUser && memoryExpanded) {
                ResponseMemoryPanel(message.memory)
            }
            if (!isUser && sourcesExpanded && publicSources.isNotEmpty()) {
                PublicSourcesPanel(
                    sources = publicSources,
                    onOpen = { source ->
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(source.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            }
        }
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
private fun PublicSourcesPanel(
    sources: List<CuriositySource>,
    onOpen: (CuriositySource) -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Fontes públicas", style = MaterialTheme.typography.titleSmall)
            Text(
                "Estas fontes pertencem à resposta de Curiosidade e não foram gravadas automaticamente como memória pessoal.",
                style = MaterialTheme.typography.bodySmall,
            )
            sources.forEachIndexed { index, source ->
                if (index > 0) HorizontalDivider()
                Text("${index + 1}. ${source.title}", style = MaterialTheme.typography.bodyMedium)
                Text(source.domain, style = MaterialTheme.typography.labelSmall)
                source.excerpt?.takeIf { it.isNotBlank() }?.let {
                    Text(it.take(320), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onOpen(source) }) { Text("Abrir fonte") }
            }
        }
    }
}

@Composable
private fun ResponseMemoryPanel(memory: ResponseMemoryMetadata?) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Memoria.ia nesta resposta", style = MaterialTheme.typography.titleSmall)
            if (memory == null) {
                Text("Esta resposta não possui metadados de memória registrados.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Status: ${memory.status}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "IDs usados: ${if (memory.memoryIds.isEmpty()) "—" else memory.memoryIds.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "IDs aprendidos: ${if (memory.learnedMemoryIds.isEmpty()) "—" else memory.learnedMemoryIds.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                memory.confidence?.let {
                    Text("Confiança: ${"%.3f".format(it)}", style = MaterialTheme.typography.bodySmall)
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
