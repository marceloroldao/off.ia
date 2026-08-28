package ia.off

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ChatMessage(val role: String, val text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { OffiaChatScreen() } }
    }
}

@Composable
fun OffiaChatScreen() {
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Offline • configure local GGUF") }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    Scaffold(
        topBar = {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("OFF.IA", style = MaterialTheme.typography.headlineMedium)
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        },
        bottomBar = {
            Column(Modifier.padding(12.dp)) {
                if (messages.isNotEmpty()) {
                    Text("Memory: pending • Inference: local", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                }
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
                        enabled = input.isNotBlank(),
                        onClick = {
                            val text = input.trim()
                            input = ""
                            messages += ChatMessage("Você", text)
                            // The next vertical slice replaces this shell response with:
                            // Memoria.resolve -> llama.cpp -> Memoria.learnTurn -> BDR.
                            messages += ChatMessage("OFF.IA", "Modelo local ainda não configurado.")
                            status = "Offline • aguardando modelo GGUF"
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
