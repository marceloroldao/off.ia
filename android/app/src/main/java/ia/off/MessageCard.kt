package ia.off

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
    var memoryExpanded by remember(message.id) { mutableStateOf(false) }
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
                text = if (isUser) "Você" else "OFF.IA",
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
                    TextButton(
                        enabled = onCuriosity != null && !busy,
                        onClick = { onCuriosity?.invoke(message.id) },
                    ) { Text("Curiosidade") }
                    TextButton(
                        enabled = onImprove != null && !busy,
                        onClick = { onImprove?.invoke(message.id) },
                    ) { Text("Melhorar") }
                    TextButton(
                        enabled = onRegenerate != null && !busy,
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
                                        subject = "Resposta do OFF.IA",
                                        text = message.text,
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Copiar resposta") },
                                onClick = {
                                    moreExpanded = false
                                    clipboard.setText(AnnotatedString(message.text))
                                },
                            )
                        }
                    }
                }
            }

            if (!isUser && memoryExpanded) {
                ResponseMemoryPanel(message.memory)
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
