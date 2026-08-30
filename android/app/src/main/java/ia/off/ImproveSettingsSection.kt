package ia.off

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ImproveSettingsSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val credentials = remember { SecureCredentialStore(context) }
    var openAiKeyDraft by remember { mutableStateOf("") }
    var geminiKeyDraft by remember { mutableStateOf("") }
    var openAiConfigured by remember { mutableStateOf(credentials.has(SecureCredentialStore.OPENAI_API_KEY)) }
    var geminiConfigured by remember { mutableStateOf(credentials.has(SecureCredentialStore.GEMINI_API_KEY)) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Melhorar resposta", style = MaterialTheme.typography.titleMedium)
        Text(
            "Escolha uma inteligência externa opcional. OFF.IA envia apenas a pergunta, a resposta local e o contexto mínimo selecionado pela Memoria.ia.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderButton(
                label = "Nenhum",
                selected = settings.improveProvider == null,
                onClick = { onSettingsChanged(settings.copy(improveProvider = null)) },
            )
            ProviderButton(
                label = "OpenAI",
                selected = settings.improveProvider == ImproveProviderKind.OPENAI,
                onClick = { onSettingsChanged(settings.copy(improveProvider = ImproveProviderKind.OPENAI)) },
            )
            ProviderButton(
                label = "Gemini",
                selected = settings.improveProvider == ImproveProviderKind.GEMINI,
                onClick = { onSettingsChanged(settings.copy(improveProvider = ImproveProviderKind.GEMINI)) },
            )
        }

        Text("MA2A • rota preparada, aguardando contrato de rede", style = MaterialTheme.typography.bodySmall)

        Text("OpenAI", style = MaterialTheme.typography.titleSmall)
        Text(
            if (openAiConfigured) "Chave configurada no Android Keystore" else "Chave não configurada",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = openAiKeyDraft,
            onValueChange = { openAiKeyDraft = it.take(512) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("OpenAI API key") },
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = openAiKeyDraft.isNotBlank(),
                onClick = {
                    credentials.put(SecureCredentialStore.OPENAI_API_KEY, openAiKeyDraft)
                    openAiKeyDraft = ""
                    openAiConfigured = true
                },
            ) { Text("Salvar chave") }
            TextButton(
                enabled = openAiConfigured,
                onClick = {
                    credentials.remove(SecureCredentialStore.OPENAI_API_KEY)
                    openAiConfigured = false
                },
            ) { Text("Remover") }
        }
        OutlinedTextField(
            value = settings.openAiModel,
            onValueChange = { onSettingsChanged(settings.copy(openAiModel = it.take(80))) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Modelo OpenAI") },
            supportingText = { Text("Padrão: gpt-5.4-mini") },
        )

        Text("Gemini", style = MaterialTheme.typography.titleSmall)
        Text(
            if (geminiConfigured) "Chave configurada no Android Keystore" else "Chave não configurada",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = geminiKeyDraft,
            onValueChange = { geminiKeyDraft = it.take(512) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Gemini API key") },
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = geminiKeyDraft.isNotBlank(),
                onClick = {
                    credentials.put(SecureCredentialStore.GEMINI_API_KEY, geminiKeyDraft)
                    geminiKeyDraft = ""
                    geminiConfigured = true
                },
            ) { Text("Salvar chave") }
            TextButton(
                enabled = geminiConfigured,
                onClick = {
                    credentials.remove(SecureCredentialStore.GEMINI_API_KEY)
                    geminiConfigured = false
                },
            ) { Text("Remover") }
        }
        OutlinedTextField(
            value = settings.geminiModel,
            onValueChange = { onSettingsChanged(settings.copy(geminiModel = it.take(80))) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Modelo Gemini") },
            supportingText = { Text("Padrão: gemini-3.7-flash") },
        )

        Text(
            "As chaves não entram na Memoria.ia, no BDR, no histórico de conversa nem nas exportações diagnósticas.",
            style = MaterialTheme.typography.labelMedium,
        )
        HorizontalDivider()
    }
}

@Composable
private fun ProviderButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick) {
        Text(if (selected) "✓ $label" else label)
    }
}
