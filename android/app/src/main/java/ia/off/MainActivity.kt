package ia.off

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class ChatMessage(val role: String, val text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { OffiaChatScreen() } }
    }
}

private fun displayNameForUri(context: android.content.Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index < 0) null else cursor.getString(index)
    }

@Composable
fun OffiaChatScreen() {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Offline • selecione um modelo GGUF") }
    var modelUri by remember { mutableStateOf<String?>(null) }
    var modelName by remember { mutableStateOf<String?>(null) }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = displayNameForUri(context, uri)
        if (name == null || !name.endsWith(".gguf", ignoreCase = true)) {
            status = "Arquivo inválido • selecione um .gguf"
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some document providers keep the current grant without offering a persistable grant.
        }
        modelUri = uri.toString()
        modelName = name
        status = "Offline • GGUF selecionado: $name"
    }

    Scaffold(
        topBar = {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("OFF.IA", style = MaterialTheme.typography.headlineMedium)
                Text(status, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { modelPicker.launch(arrayOf("*/*")) }) {
                    Text(if (modelUri == null) "Selecionar modelo GGUF" else "Trocar modelo")
                }
            }
        },
        bottomBar = {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "Memoria: integração pendente • Inferência: ${if (modelUri == null) "sem modelo" else "GGUF local selecionado"}",
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Digite uma mensagem…") },
                        singleLine = false
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = input.isNotBlank() && modelUri != null,
                        onClick = {
                            val text = input.trim()
                            input = ""
                            messages += ChatMessage("Você", text)
                            messages += ChatMessage(
                                "OFF.IA",
                                "Modelo ${modelName ?: "GGUF"} selecionado. O motor llama.cpp/JNI será conectado no próximo corte vertical."
                            )
                            status = "Offline • modelo selecionado • motor nativo pendente"
                        }
                    ) { Text("Enviar") }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { message ->
                Column {
                    Text(message.role, style = MaterialTheme.typography.labelMedium)
                    Text(message.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
