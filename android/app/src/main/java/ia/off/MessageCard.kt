package ia.off

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun MessageCard(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val isUser = message.role == "Você"
    var memoryExpanded by remember(message.id) { mutableStateOf(false) }

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
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            if (!isUser && message.text != "…") {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }) {
                        Text("Copiar")
                    }
                    TextButton(onClick = { memoryExpanded = !memoryExpanded }) {
                        Text(if (memoryExpanded) "Ocultar memória" else "Memória")
                    }
                    TextButton(enabled = false, onClick = {}) { Text("Regenerar") }
                    TextButton(enabled = false, onClick = {}) { Text("⋯") }
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
                return@Column
            }

            Text("Status: ${memory.status}", style = MaterialTheme.typography.bodySmall)
            Text(
                "IDs: ${if (memory.memoryIds.isEmpty()) "—" else memory.memoryIds.joinToString()}",
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
