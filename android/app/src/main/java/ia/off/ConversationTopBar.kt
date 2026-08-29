package ia.off

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun ConversationTopBar(
    status: String,
    modelSummary: String?,
    activeSession: ChatSession,
    sessions: List<ChatSession>,
    busy: Boolean,
    memoryAvailable: Boolean,
    onSelectSession: (String) -> Unit,
    onNewConversation: () -> Unit,
    onRenameConversation: (String) -> Unit,
    onDeleteConversation: () -> Unit,
    onChooseModel: () -> Unit,
    onExportMemory: () -> Unit,
    onCopiedConversation: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var sessionMenuExpanded by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var renameDialogVisible by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var renameText by remember(activeSession.id) { mutableStateOf(activeSession.title) }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("OFF.IA", style = MaterialTheme.typography.headlineMedium)
        Text(status, style = MaterialTheme.typography.bodySmall)
        modelSummary?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(enabled = !busy, onClick = { sessionMenuExpanded = true }) {
                    Text(activeSession.title.take(18))
                }
                DropdownMenu(expanded = sessionMenuExpanded, onDismissRequest = { sessionMenuExpanded = false }) {
                    sessions.sortedByDescending { it.updatedAt }.forEach { session ->
                        DropdownMenuItem(
                            text = { Text(session.title) },
                            onClick = {
                                sessionMenuExpanded = false
                                onSelectSession(session.id)
                            },
                        )
                    }
                }
            }

            OutlinedButton(enabled = !busy, onClick = onNewConversation) { Text("Nova") }

            Box {
                OutlinedButton(enabled = !busy, onClick = { actionsExpanded = true }) { Text("⋮") }
                DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Renomear conversa") },
                        onClick = {
                            actionsExpanded = false
                            renameText = activeSession.title
                            renameDialogVisible = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copiar conversa") },
                        onClick = {
                            actionsExpanded = false
                            clipboard.setText(AnnotatedString(activeSession.toPlainText()))
                            onCopiedConversation()
                        },
                    )
                    DropdownMenuItem(
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
                        text = { Text("Excluir conversa") },
                        onClick = {
                            actionsExpanded = false
                            deleteDialogVisible = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Configurações") },
                        onClick = {
                            actionsExpanded = false
                            settingsVisible = true
                        },
                    )
                }
            }

            OutlinedButton(enabled = !busy, onClick = onChooseModel) {
                Text("Modelo")
            }
        }

        TextButton(enabled = memoryAvailable && !busy, onClick = onExportMemory) {
            Text("Exportar Memoria.ia")
        }
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
            onChooseModel = {
                settingsVisible = false
                onChooseModel()
            },
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
