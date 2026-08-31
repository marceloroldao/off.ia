package ia.off

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ConversationTopBar(
    status: String,
    modelSummary: String?,
    activeSession: ChatSession,
    sessions: List<ChatSession>,
    busy: Boolean,
    memoryAvailable: Boolean,
    modelDownloadState: ModelDownloadState,
    onSelectSession: (String) -> Unit,
    onNewConversation: () -> Unit,
    onRenameConversation: (String) -> Unit,
    onDeleteConversation: () -> Unit,
    onChooseModel: () -> Unit,
    onDownloadDefaultModel: () -> Unit,
    onCancelModelDownload: () -> Unit,
    onExportMemory: () -> Unit,
    onCopiedConversation: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val downloadActive = modelDownloadState is ModelDownloadState.Downloading ||
        modelDownloadState is ModelDownloadState.Verifying
    var conversationsExpanded by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var renameDialogVisible by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    var searchDialogVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var renameText by remember(activeSession.id) { mutableStateOf(activeSession.title) }

    Surface(tonalElevation = 1.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box {
                    TextButton(
                        enabled = !busy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        onClick = { conversationsExpanded = true },
                    ) { Text("☰", style = MaterialTheme.typography.titleLarge) }
                    DropdownMenu(
                        expanded = conversationsExpanded,
                        onDismissRequest = { conversationsExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("＋ Nova conversa") },
                            onClick = {
                                conversationsExpanded = false
                                onNewConversation()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("⌕ Buscar conversas") },
                            onClick = {
                                conversationsExpanded = false
                                searchDialogVisible = true
                            },
                        )
                        sessions.sortedByDescending { it.updatedAt }.take(10).forEach { session ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (session.id == activeSession.id) "✓ ${session.title}" else session.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = {
                                    conversationsExpanded = false
                                    onSelectSession(session.id)
                                },
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeSession.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = status,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                TextButton(
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    onClick = onNewConversation,
                ) { Text("＋", style = MaterialTheme.typography.titleLarge) }

                Box {
                    TextButton(
                        enabled = !busy || downloadActive,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        onClick = { actionsExpanded = true },
                    ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                    DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Buscar conversas") },
                            onClick = {
                                actionsExpanded = false
                                searchDialogVisible = true
                            },
                        )
                        DropdownMenuItem(
                            enabled = !busy,
                            text = { Text("Renomear conversa") },
                            onClick = {
                                actionsExpanded = false
                                renameText = activeSession.title
                                renameDialogVisible = true
                            },
                        )
                        DropdownMenuItem(
                            enabled = !busy,
                            text = { Text("Copiar conversa") },
                            onClick = {
                                actionsExpanded = false
                                clipboard.setText(AnnotatedString(activeSession.toPlainText()))
                                onCopiedConversation()
                            },
                        )
                        DropdownMenuItem(
                            enabled = !busy,
                            text = { Text("Compartilhar conversa") },
                            onClick = {
                                actionsExpanded = false
                                sharePlainText(
                                    context = context.applicationContext,
                                    subject = activeSession.title,
                                    text = activeSession.toPlainText(),
                                )
                            },
                        )
                        DropdownMenuItem(
                            enabled = memoryAvailable && !busy,
                            text = { Text("Exportar Memoria.ia") },
                            onClick = {
                                actionsExpanded = false
                                onExportMemory()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Configurações") },
                            onClick = {
                                actionsExpanded = false
                                settingsVisible = true
                            },
                        )
                        DropdownMenuItem(
                            enabled = !busy,
                            text = { Text("Excluir conversa") },
                            onClick = {
                                actionsExpanded = false
                                deleteDialogVisible = true
                            },
                        )
                    }
                }
            }

            modelSummary?.let {
                Text(
                    text = it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 48.dp, end = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            if (downloadActive) {
                TextButton(
                    modifier = Modifier.padding(start = 40.dp),
                    onClick = onCancelModelDownload,
                ) { Text("Cancelar download") }
            }
        }
    }

    if (searchDialogVisible) {
        ConversationSearchDialog(
            sessions = sessions,
            activeSessionId = activeSession.id,
            onSelect = onSelectSession,
            onDismiss = { searchDialogVisible = false },
        )
    }

    if (renameDialogVisible) {
        AlertDialog(
            onDismissRequest = { renameDialogVisible = false },
            title = { Text("Renomear conversa") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(80) },
                    singleLine = true,
                    label = { Text("Nome") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.trim().isNotEmpty(),
                    onClick = {
                        onRenameConversation(renameText.trim())
                        renameDialogVisible = false
                    },
                ) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogVisible = false }) { Text("Cancelar") }
            },
        )
    }

    if (deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text("Excluir conversa?") },
            text = { Text("O histórico visual desta conversa será removido. A Memoria.ia não será apagada.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteConversation()
                    deleteDialogVisible = false
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogVisible = false }) { Text("Cancelar") }
            },
        )
    }

    if (settingsVisible) {
        SettingsPanel(
            modelSummary = modelSummary,
            memoryAvailable = memoryAvailable,
            modelDownloadState = modelDownloadState,
            onChooseModel = {
                settingsVisible = false
                onChooseModel()
            },
            onDownloadDefaultModel = onDownloadDefaultModel,
            onCancelModelDownload = onCancelModelDownload,
            onExportMemory = onExportMemory,
            onDismiss = { settingsVisible = false },
        )
    }
}

fun ChatSession.toPlainText(): String = buildString {
    append(title)
    append("\n\n")
    messages.forEach { message ->
        append(message.role)
        append(": ")
        append(message.text)
        append("\n\n")
    }
}.trim()
