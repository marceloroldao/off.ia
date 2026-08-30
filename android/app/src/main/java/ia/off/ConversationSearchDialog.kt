package ia.off

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class ConversationSearchHit(
    val session: ChatSession,
    val preview: String,
)

@Composable
fun ConversationSearchDialog(
    sessions: List<ChatSession>,
    activeSessionId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val normalized = query.trim().lowercase()
    val hits = remember(sessions, normalized) {
        sessions
            .asSequence()
            .sortedByDescending { it.updatedAt }
            .mapNotNull { session ->
                if (normalized.isBlank()) {
                    ConversationSearchHit(
                        session = session,
                        preview = session.messages.lastOrNull()?.text?.singleLinePreview().orEmpty(),
                    )
                } else {
                    val titleMatch = session.title.lowercase().contains(normalized)
                    val messageMatch = session.messages.firstOrNull { message ->
                        message.text.lowercase().contains(normalized)
                    }
                    if (!titleMatch && messageMatch == null) null
                    else ConversationSearchHit(
                        session = session,
                        preview = (messageMatch?.text ?: session.messages.lastOrNull()?.text.orEmpty()).singleLinePreview(),
                    )
                }
            }
            .take(50)
            .toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conversas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Buscar conversas") },
                    placeholder = { Text("Título ou conteúdo") },
                )
                Text(
                    if (normalized.isBlank()) "Recentes" else "${hits.size} resultado(s)",
                    style = MaterialTheme.typography.labelMedium,
                )
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(hits, key = { it.session.id }) { hit ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(hit.session.id)
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(hit.session.title, style = MaterialTheme.typography.bodyLarge)
                                if (hit.session.id == activeSessionId) {
                                    Text("• atual", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (hit.preview.isNotBlank()) {
                                Text(hit.preview, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                        HorizontalDivider()
                    }
                }
                if (hits.isEmpty()) {
                    Text("Nenhuma conversa encontrada.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        },
    )
}

private fun String.singleLinePreview(): String =
    replace(Regex("\\s+"), " ").trim().take(180)
