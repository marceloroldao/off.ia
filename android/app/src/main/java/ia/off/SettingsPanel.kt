package ia.off

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun shortIdentity(value: String): String =
    if (value == "desconhecida") value else value.take(8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    modelSummary: String?,
    memoryAvailable: Boolean,
    modelDownloadState: ModelDownloadState,
    onChooseModel: () -> Unit,
    onDownloadDefaultModel: () -> Unit,
    onCancelModelDownload: () -> Unit,
    onExportMemory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hostContext = LocalContext.current
    val context = hostContext.applicationContext
    val store = remember { AppSettingsStore(context) }
    val modelManager = remember { ModelManager(context) }
    val deviceIdentity = remember { MemoriaDeviceIdentityStore(context) }
    val pairingScope = rememberCoroutineScope()
    var deviceBinding by remember { mutableStateOf(runCatching { deviceIdentity.loadBinding() }.getOrNull()) }
    var serverUrl by remember { mutableStateOf(deviceBinding?.serverBaseUrl.orEmpty()) }
    var enrollmentCode by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("OFF.IA Android") }
    var pairingBusy by remember { mutableStateOf(false) }
    var pairingStatus by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(store.load()) }
    var installedModels by remember { mutableStateOf(modelManager.installedModels()) }
    var deleteCandidate by remember { mutableStateOf<InstalledModel?>(null) }
    var detailsCandidate by remember { mutableStateOf<InstalledModel?>(null) }
    var modelSelectionError by remember { mutableStateOf<String?>(null) }
    val diagnosticScope = rememberCoroutineScope()
    var v2DiagnosticRunning by remember { mutableStateOf(false) }
    var v2DiagnosticReport by remember { mutableStateOf<MemoryRegressionReport?>(null) }
    var v2DiagnosticError by remember { mutableStateOf<String?>(null) }
    val defaultModel = ModelCatalog.defaultModel
    val downloadActive = modelDownloadState is ModelDownloadState.Downloading ||
        modelDownloadState is ModelDownloadState.Verifying

    LaunchedEffect(modelDownloadState) {
        if (modelDownloadState is ModelDownloadState.Ready) {
            installedModels = modelManager.installedModels()
        }
    }

    fun update(next: AppSettings) {
        settings = next
        store.save(next)
    }

    fun selectModel(model: InstalledModel) {
        if (model.active || !model.valid || downloadActive) return
        val result = runCatching { modelManager.select(model) }
        result.onSuccess {
            modelSelectionError = null
            installedModels = modelManager.installedModels()
            onDismiss()
            hostContext.findActivity()?.recreate()
        }.onFailure { error ->
            modelSelectionError = error.message ?: error.javaClass.simpleName
        }
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
                "O chat local usa os registros da Memoria.ia sem LLM. O modelo llama.cpp fica reservado à Curiosidade, que exige acesso online explícito.",
                style = MaterialTheme.typography.bodySmall,
            )

            SettingsSection("Modelos") {
                Text(modelSummary ?: "Nenhum modelo carregado", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${installedModels.size} instalado(s) • ${modelManager.storageBytes().formatStorageSize()}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "Toque em um modelo instalado para ativá-lo. Use Detalhes para inspecionar o arquivo sem trocar o modelo ativo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                installedModels.forEach { model ->
                    InstalledModelRow(
                        model = model,
                        enabled = !downloadActive,
                        onSelect = { selectModel(model) },
                        onDetails = { detailsCandidate = model },
                        onDelete = { deleteCandidate = model },
                    )
                }
                modelSelectionError?.let { error ->
                    Text("Falha ao selecionar modelo: $error", style = MaterialTheme.typography.bodySmall)
                }

                Text("Modelo padrão", style = MaterialTheme.typography.titleSmall)
                Text(defaultModel.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${defaultModel.expectedSizeBytes?.formatStorageSize() ?: "tamanho desconhecido"} • ${defaultModel.licenseName}",
                    style = MaterialTheme.typography.bodySmall,
                )
                ModelDownloadStatus(
                    state = modelDownloadState,
                    onCancel = onCancelModelDownload,
                )

                if (!downloadActive) {
                    OutlinedButton(onClick = onDownloadDefaultModel) {
                        Text("Baixar modelo padrão")
                    }
                }
                OutlinedButton(enabled = !downloadActive, onClick = onChooseModel) {
                    Text("Escolher / importar outro GGUF")
                }
            }

            SettingsSection("Memoria.ia") {
                Text(
                    if (memoryAvailable) "Ativa • armazenamento local" else "Indisponível",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(enabled = memoryAvailable && !downloadActive, onClick = onExportMemory) {
                    Text("Exportar diagnóstico")
                }

                HorizontalDivider()
                Text("Servidor Memoria.ia", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Pareamento opcional para memória estrutural V2. A chave administrativa do servidor nunca é armazenada no OFF.IA.",
                    style = MaterialTheme.typography.bodySmall,
                )

                val binding = deviceBinding
                if (binding == null) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        enabled = !pairingBusy,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("URL do servidor") },
                        placeholder = { Text("https://memoria.exemplo") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = enrollmentCode,
                        onValueChange = { enrollmentCode = it },
                        enabled = !pairingBusy,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Código de enrollment") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        enabled = !pairingBusy,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome deste OFF.IA") },
                        singleLine = true,
                    )
                    OutlinedButton(
                        enabled = !pairingBusy &&
                            serverUrl.isNotBlank() &&
                            enrollmentCode.isNotBlank() &&
                            deviceName.isNotBlank(),
                        onClick = {
                            pairingBusy = true
                            pairingStatus = "Enviando chave pública para enrollment…"
                            pairingScope.launch {
                                try {
                                    val result = MemoriaDeviceEnrollmentClient(deviceIdentity).claim(
                                        serverBaseUrl = serverUrl,
                                        enrollmentCode = enrollmentCode,
                                        deviceName = deviceName,
                                    )
                                    deviceBinding = deviceIdentity.loadBinding()
                                    enrollmentCode = ""
                                    pairingStatus = when (result.status) {
                                        "pending_approval" ->
                                            "Dispositivo registrado • aguardando aprovação no servidor"
                                        else -> "Enrollment concluído • ${result.status}"
                                    }
                                } catch (error: Exception) {
                                    pairingStatus = "Falha no enrollment • ${error.message ?: error.javaClass.simpleName}"
                                } finally {
                                    pairingBusy = false
                                }
                            }
                        },
                    ) {
                        Text(if (pairingBusy) "Vinculando…" else "Vincular ao servidor")
                    }
                } else {
                    Text(
                        "Vinculado • ${binding.serverBaseUrl}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "device_id: ${binding.deviceId}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        enabled = !pairingBusy,
                        onClick = {
                            pairingBusy = true
                            pairingStatus = "Autenticando dispositivo e validando memory.sync…"
                            pairingScope.launch {
                                try {
                                    val tokenProvider = MemoriaDeviceTokenProvider(deviceIdentity)
                                    val structural = MemoriaServerStructuralClient(
                                        binding.serverBaseUrl,
                                        tokenProvider,
                                    )
                                    val probe = structural.resolve(
                                        query = "__offia_memory_sync_probe__",
                                        limit = 1,
                                        maxScan = 1,
                                    )
                                    pairingStatus =
                                        "Servidor autenticado • memory.sync OK • ${probe.status}"
                                } catch (error: Exception) {
                                    pairingStatus =
                                        "Servidor ainda não autorizou o dispositivo • ${error.message ?: error.javaClass.simpleName}"
                                } finally {
                                    pairingBusy = false
                                }
                            }
                        },
                    ) {
                        Text(if (pairingBusy) "Testando…" else "Testar memory.sync")
                    }
                    Text(
                        "O vínculo é intencionalmente fixo. Trocar de servidor/dispositivo exige um fluxo explícito de rotação, ainda não implementado.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                pairingStatus?.let { status ->
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }

            SettingsSection("Modelos e rede") {
                SettingsSwitch(
                    title = "Baixar modelo padrão automaticamente",
                    subtitle = "Para Curiosidade, OFF.IA tenta baixar e carregar o modelo padrão ao iniciar quando não houver um instalado.",
                    checked = settings.autoDownloadDefaultModel,
                    onCheckedChange = { update(settings.copy(autoDownloadDefaultModel = it)) },
                )
                SettingsSwitch(
                    title = "Baixar modelos somente no Wi-Fi",
                    subtitle = "Quando ativado, o download automático aguarda uma conexão Wi-Fi válida.",
                    checked = settings.wifiOnlyModelDownloads,
                    onCheckedChange = { update(settings.copy(wifiOnlyModelDownloads = it)) },
                )
                SettingsSwitch(
                    title = "Bloquear recursos online",
                    subtitle = "Quando ativado, Curiosidade fica bloqueada após o download do modelo.",
                    checked = settings.blockNetworkAfterModelDownload,
                    onCheckedChange = { update(settings.copy(blockNetworkAfterModelDownload = it)) },
                )
            }

            SettingsSection("Privacidade") {
                Text("Chat: Memoria.ia local, sem LLM", style = MaterialTheme.typography.labelMedium)
                Text("LLM local: somente Curiosidade", style = MaterialTheme.typography.labelMedium)
                Text("Memória: Memoria.ia + BDR local", style = MaterialTheme.typography.labelMedium)
            }

            SettingsSection("Desenvolvimento") {
                SettingsSwitch(
                    title = "Modo laboratório",
                    subtitle = "Mantém diagnósticos técnicos detalhados disponíveis durante os testes.",
                    checked = settings.laboratoryMode,
                    onCheckedChange = { update(settings.copy(laboratoryMode = it)) },
                )
                if (settings.laboratoryMode) {
                    val diagnostics = listOf(
                        "OFF.IA: ${BuildConfig.VERSION_NAME}",
                        "OFF.IA commit: ${BuildConfig.OFFIA_COMMIT}",
                        "Memoria.ia: ${BuildConfig.MEMORIA_IA_VERSION}",
                        "Memoria.ia commit: ${BuildConfig.MEMORIA_IA_COMMIT}",
                        "BDR: ${BuildConfig.BDR_VERSION}",
                        "BDR commit: ${BuildConfig.BDR_COMMIT}",
                        "llama.cpp commit: ${BuildConfig.LLAMA_CPP_COMMIT}",
                        "KV-cache: ${BuildConfig.KV_CACHE_TYPE}",
                        "ABI móvel: ${BuildConfig.MEMORIA_MOBILE_ABI}",
                    ).joinToString("\n")

                    Text("OFF.IA: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium)
                    Text("OFF.IA commit: ${shortIdentity(BuildConfig.OFFIA_COMMIT)}", style = MaterialTheme.typography.bodySmall)
                    Text("Memoria.ia: ${BuildConfig.MEMORIA_IA_VERSION}", style = MaterialTheme.typography.labelMedium)
                    Text("Memoria.ia commit: ${shortIdentity(BuildConfig.MEMORIA_IA_COMMIT)}", style = MaterialTheme.typography.bodySmall)
                    Text("BDR: ${BuildConfig.BDR_VERSION}", style = MaterialTheme.typography.labelMedium)
                    Text("BDR commit: ${shortIdentity(BuildConfig.BDR_COMMIT)}", style = MaterialTheme.typography.bodySmall)
                    Text("llama.cpp commit: ${shortIdentity(BuildConfig.LLAMA_CPP_COMMIT)}", style = MaterialTheme.typography.bodySmall)
                    Text("KV-cache: ${BuildConfig.KV_CACHE_TYPE}", style = MaterialTheme.typography.bodySmall)
                    Text("ABI móvel: ${BuildConfig.MEMORIA_MOBILE_ABI}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("OFF.IA diagnóstico", diagnostics))
                    }) {
                        Text("Copiar diagnóstico completo")
                    }

                    HorizontalDivider()
                    Text(
                        "Gate Memoria.ia V2",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Executa a bateria estrutural contra o runtime nativo + BDR em uma base isolada. A memória real das conversas não é modificada.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        enabled = memoryAvailable && !v2DiagnosticRunning,
                        onClick = {
                            v2DiagnosticRunning = true
                            v2DiagnosticReport = null
                            v2DiagnosticError = null
                            diagnosticScope.launch {
                                runCatching {
                                    runStructuralV2DeviceDiagnostics(context)
                                }.onSuccess { report ->
                                    v2DiagnosticReport = report
                                }.onFailure { error ->
                                    v2DiagnosticError = error.message ?: error.javaClass.simpleName
                                }
                                v2DiagnosticRunning = false
                            }
                        },
                    ) {
                        Text(
                            if (v2DiagnosticRunning) {
                                "Testando Memoria.ia V2…"
                            } else {
                                "Testar Memoria.ia V2 no aparelho"
                            },
                        )
                    }

                    if (v2DiagnosticRunning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    v2DiagnosticError?.let { error ->
                        Text(
                            "Falha no diagnóstico V2: $error",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    v2DiagnosticReport?.let { report ->
                        val allPassed =
                            report.passed == report.results.size &&
                            report.failed == 0 &&
                            report.restartRequired == 0 &&
                            report.unavailable == 0
                        Text(
                            if (allPassed) {
                                "V2: PASS " + report.passed + "/" + report.results.size
                            } else {
                                "V2: " + report.passed + "/" + report.results.size +
                                    " PASS • " + report.failed + " FAIL"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                        report.results.forEach { result ->
                            Text(
                                buildString {
                                    append(
                                        if (result.outcome == MemoryRegressionOutcome.PASS) "✓ " else "✗ ",
                                    )
                                    append(result.scenario.id)
                                    append(" • ")
                                    append(result.status.name)
                                    append(" • ")
                                    append(result.latencyMs)
                                    append(" ms")
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedButton(onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    "OFF.IA Memoria.ia V2",
                                    report.toStructuralV2DiagnosticText(),
                                ),
                            )
                        }) {
                            Text("Copiar relatório V2")
                        }
                    }
                }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Fechar")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    detailsCandidate?.let { candidate ->
        val gguf = candidate.ggufVersion?.let { "GGUF v$it" } ?: "GGUF inválido/desconhecido"
        val runtime = if (candidate.active) modelSummary else null
        AlertDialog(
            onDismissRequest = { detailsCandidate = null },
            title = { Text("Detalhes do modelo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(candidate.name, style = MaterialTheme.typography.titleSmall)
                    Text("Estado: ${if (candidate.active) "Instalado • ativo ✓" else "Instalado"}")
                    Text("Formato: $gguf")
                    Text("Tamanho: ${candidate.sizeBytes.formatStorageSize()}")
                    Text("Armazenamento: privado da OFF.IA")
                    if (!runtime.isNullOrBlank()) {
                        HorizontalDivider()
                        Text("Runtime carregado", style = MaterialTheme.typography.labelMedium)
                        Text(runtime, style = MaterialTheme.typography.bodySmall)
                    } else if (!candidate.active) {
                        Text(
                            "Arquitetura e template são confirmados pelo llama.cpp quando este modelo é carregado.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsCandidate = null }) { Text("Fechar") }
            },
        )
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Excluir modelo?") },
            text = {
                Text("${candidate.name}\n${candidate.sizeBytes.formatStorageSize()}\n\nO modelo GGUF será removido do armazenamento local.")
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { modelManager.delete(candidate) }
                    installedModels = modelManager.installedModels()
                    deleteCandidate = null
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun ModelDownloadStatus(
    state: ModelDownloadState,
    onCancel: () -> Unit,
) {
    when (state) {
        ModelDownloadState.Idle -> Unit
        is ModelDownloadState.Downloading -> {
            val total = state.totalBytes
            if (total != null && total > 0L) {
                val progress = (state.bytesDownloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Baixando ${state.bytesDownloaded.formatStorageSize()} / ${total.formatStorageSize()} • ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Baixando ${state.bytesDownloaded.formatStorageSize()}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onCancel) { Text("Cancelar download") }
        }
        ModelDownloadState.Verifying -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Verificando SHA-256…", style = MaterialTheme.typography.bodySmall)
        }
        is ModelDownloadState.Ready -> Text("Modelo baixado e validado", style = MaterialTheme.typography.bodySmall)
        is ModelDownloadState.Failed -> Text("Falha: ${state.message}", style = MaterialTheme.typography.bodySmall)
        ModelDownloadState.Cancelled -> Text("Download cancelado", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InstalledModelRow(
    model: InstalledModel,
    enabled: Boolean,
    onSelect: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && model.valid && !model.active, onClick = onSelect)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (model.active) "${model.name} • Ativo ✓" else model.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            val gguf = model.ggufVersion?.let { "GGUF v$it" } ?: "GGUF inválido/desconhecido"
            val state = if (model.active) "Instalado • ativo" else "Instalado • toque para ativar"
            Text("$gguf • ${model.sizeBytes.formatStorageSize()} • $state", style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = onDetails) { Text("Detalhes") }
            TextButton(enabled = enabled && !model.active, onClick = onDelete) { Text("Excluir") }
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
