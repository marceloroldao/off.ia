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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                    label = { Text("Buscar") },
                    placeholder = { Text("Título ou conteúdo da conversa") },
                )
                Text(
                    if (normalized.isBlank()) "Conversas recentes" else "${hits.size} resultado(s)",
                    style = MaterialTheme.typography.labelMedium,
                )
                LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                    items(hits, key = { it.session.id }) { hit ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(hit.session.id)
                                    onDismiss()
                                }
                                .padding(vertical = 11.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    hit.session.title,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (hit.session.id == activeSessionId) {
                                    Text("Atual", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                if (hit.preview.isNotBlank()) {
                                    Text(
                                        hit.preview,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                formatConversationTime(hit.session.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        HorizontalDivider()
                    }
                }
                if (hits.isEmpty()) {
                    Text(
                        if (normalized.isBlank()) "Nenhuma conversa salva ainda." else "Nenhuma conversa encontrada para “${query.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        },
    )
}

private fun formatConversationTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val formatter = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun String.singleLinePreview(): String =
    replace(Regex("\\s+"), " ").trim().take(180)
