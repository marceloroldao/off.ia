package ia.off

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ImproveSettingsSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val keepSettingsCallback = onSettingsChanged

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Rede de inteligência", style = MaterialTheme.typography.titleMedium)
        Text(
            "OFF.IA não se conecta diretamente a APIs de OpenAI, Gemini ou outros transformers.",
            style = MaterialTheme.typography.bodySmall,
        )

        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("M2A2", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "OFF.IA → M2A2 → servidor Memoria.ia → transformers",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "O servidor Memoria.ia será responsável por identidade, autorização, seleção de transformer, políticas e roteamento. O aplicativo recebe apenas o resultado autorizado pela rede.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Estado atual: aguardando integração do contrato M2A2.",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Text(
            "OpenAI/Gemini permanecem reconhecíveis somente em históricos antigos. Novas respostas nunca usam essas APIs diretamente.",
            style = MaterialTheme.typography.labelSmall,
        )
        HorizontalDivider()
    }
}
