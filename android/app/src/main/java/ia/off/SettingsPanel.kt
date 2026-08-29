package ia.off

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsPanel(
    modelSummary: String?,
    memoryAvailable: Boolean,
    onChooseModel: () -> Unit,
    onExportMemory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val store = remember { AppSettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }

    fun update(next: AppSettings) {
        settings = next
        store.save(next)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Configurações", style = MaterialTheme.typography.headlineSmall)
            Text(
                "O chat, a Memoria.ia, o BDR e a inferência continuam locais. Recursos de rede ainda não estão habilitados neste build.",
                style = MaterialTheme.typography.bodySmall,
            )

            SettingsSection("Modelo") {
                Text(modelSummary ?: "Nenhum modelo carregado", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onChooseModel) { Text("Escolher / importar GGUF") }
            }

            SettingsSection("Memoria.ia") {
                Text(
                    if (memoryAvailable) "Ativa • armazenamento local" else "Indisponível",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(enabled = memoryAvailable, onClick = onExportMemory) {
                    Text("Exportar diagnóstico")
                }
            }

            SettingsSection("Modelos e rede") {
                SettingsSwitch(
                    title = "Baixar modelos grandes somente no Wi-Fi",
                    subtitle = "Será aplicado quando o gerenciador de download online for habilitado.",
                    checked = settings.wifiOnlyModelDownloads,
                    onCheckedChange = { update(settings.copy(wifiOnlyModelDownloads = it)) },
                )
                SettingsSwitch(
                    title = "Bloquear rede depois de baixar o modelo",
                    subtitle = "Preferência preparada para manter o uso cotidiano estritamente local.",
                    checked = settings.blockNetworkAfterModelDownload,
                    onCheckedChange = { update(settings.copy(blockNetworkAfterModelDownload = it)) },
                )
            }

            SettingsSection("Privacidade") {
                SettingsSwitch(
                    title = "Confirmar antes de enviar para nuvem",
                    subtitle = "OpenAI/Gemini nunca receberão dados sem a política configurada pelo usuário.",
                    checked = settings.confirmBeforeCloud,
                    onCheckedChange = { update(settings.copy(confirmBeforeCloud = it)) },
                )
                Text("Internet: desativada neste build", style = MaterialTheme.typography.labelMedium)
                Text("Inferência: llama.cpp local", style = MaterialTheme.typography.labelMedium)
                Text("Memória: Memoria.ia + BDR local", style = MaterialTheme.typography.labelMedium)
            }

            SettingsSection("Desenvolvimento") {
                SettingsSwitch(
                    title = "Modo laboratório",
                    subtitle = "Mantém diagnósticos técnicos detalhados disponíveis durante os testes.",
                    checked = settings.laboratoryMode,
                    onCheckedChange = { update(settings.copy(laboratoryMode = it)) },
                )
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Fechar")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
        HorizontalDivider()
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
