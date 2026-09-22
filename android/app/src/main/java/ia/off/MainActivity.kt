package ia.off

import android.content.Context
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
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.UnsupportedArchitectureException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufProbe(
    val sizeBytes: Long,
    val version: Int,
    val runtimeProfile: ModelRuntimeProfile? = null,
)

private const val PREFS = "offia-local"
private const val PREF_MODEL_PATH = "model-path"
private const val PREF_MODEL_NAME = "model-name"
private const val ACTIVE_TRAJECTORY_TURNS = 8
private const val SYSTEM_PROMPT =
    "Você é OFF.IA, um assistente local e offline. Responda de forma clara e concisa. " +
        "Não afirme que consultou a internet. Quando contexto da Memoria.ia for fornecido, priorize esse contexto."

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { OffiaChatScreen() } }
    }
}

private fun displayNameForUri(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index < 0) null else cursor.getString(index)
    }

private fun probeGguf(file: File): GgufProbe {
    require(file.isFile && file.canRead()) { "Arquivo do modelo não pode ser lido" }
    require(file.length() >= 16L) { "Arquivo muito pequeno para ser um GGUF válido" }

    val header = ByteArray(8)
    file.inputStream().use { input ->
        val read = input.read(header)
        require(read == header.size) { "Cabeçalho GGUF incompleto" }
    }

    val magic = header.copyOfRange(0, 4).toString(Charsets.US_ASCII)
    require(magic == "GGUF") { "Arquivo inválido: cabeçalho '$magic' em vez de GGUF." }

    val version = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
    require(version in 2..3) { "Versão GGUF $version não reconhecida" }
    return GgufProbe(file.length(), version)
}

private fun modelLoadMessage(error: Exception, probe: GgufProbe?): String = when (error) {
    is UnsupportedArchitectureException -> {
        val details = probe?.let { "GGUF v${it.version}, ${it.sizeBytes / (1024 * 1024)} MB" } ?: "GGUF não diagnosticado"
        "O arquivo tem cabeçalho GGUF válido ($details), mas o llama.cpp não conseguiu abrir o modelo."
    }
    else -> error.message ?: error.javaClass.simpleName
}

private suspend fun importModel(context: Context, uri: Uri, displayName: String): Pair<File, GgufProbe> =
    withContext(Dispatchers.IO) {
        val models = File(context.filesDir, "models").apply { mkdirs() }
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(models, safeName)
        val temp = File(models, "$safeName.part")

        if (temp.exists()) temp.delete()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Não foi possível abrir o modelo selecionado" }
            FileOutputStream(temp).use { output ->
                input.copyTo(output, bufferSize = 1024 * 1024)
                output.fd.sync()
            }
        }

        val probe = try { probeGguf(temp) } catch (e: Exception) {
            temp.delete()
            throw e
        }
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Falha ao concluir a importação do GGUF" }
        target to probe
    }

private suspend fun waitUntilInitialized(engine: InferenceEngine) {
    when (engine.state.value) {
        is InferenceEngine.State.Initialized, is InferenceEngine.State.ModelReady -> return
        else -> Unit
    }
    val state = engine.state.first {
        it is InferenceEngine.State.Initialized || it is InferenceEngine.State.ModelReady || it is InferenceEngine.State.Error
    }
    if (state is InferenceEngine.State.Error) throw state.exception
}

private suspend fun loadLocalModel(engine: InferenceEngine, file: File): GgufProbe {
    val probe = withContext(Dispatchers.IO) { probeGguf(file) }
    waitUntilInitialized(engine)
    when (engine.state.value) {
        is InferenceEngine.State.ModelReady, is InferenceEngine.State.Error -> withContext(Dispatchers.IO) { engine.cleanUp() }
        else -> Unit
    }
    try {
        engine.loadModel(file.absolutePath)
        val runtimeProfile = parseModelRuntimeProfile(engine.modelMetadata())
        engine.setSystemPrompt(SYSTEM_PROMPT)
        return probe.copy(runtimeProfile = runtimeProfile)
    } catch (e: Exception) {
        throw IOException(modelLoadMessage(e, probe), e)
    }
    error("Unreachable after successful model load")
}

private fun downloadStatusText(
    state: ModelDownloadState,
    descriptor: ModelDownloadDescriptor,
): String = when (state) {
    ModelDownloadState.Idle -> "Online • preparando download do modelo"
    is ModelDownloadState.Downloading -> {
        val total = state.totalBytes
        if (total != null && total > 0L) {
            val percent = ((state.bytesDownloaded * 100L) / total).coerceIn(0L, 100L)
            "Online • baixando ${descriptor.displayName} • $percent%"
        } else {
            "Online • baixando ${descriptor.displayName} • ${state.bytesDownloaded.formatStorageSize()}"
        }
    }
    ModelDownloadState.Verifying -> "Local • verificando SHA-256 do modelo…"
    is ModelDownloadState.Ready -> "Local • modelo baixado e validado"
    is ModelDownloadState.Failed -> "Falha no download • ${state.message}"
    ModelDownloadState.Cancelled -> "Download do modelo cancelado"
}

@Composable
fun OffiaChatScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val prefs = remember { appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val settingsStore = remember { AppSettingsStore(appContext) }
    val structuralIdentity = remember { MemoriaDeviceIdentityStore(appContext) }
    val structuralTokenProvider = remember { MemoriaDeviceTokenProvider(structuralIdentity) }
    val modelManager = remember { ModelManager(appContext) }
    val modelDownloader = remember { HttpModelDownloadProvider(appContext) }
    val curiosityProvider: CuriosityProvider = remember { WikipediaCuriosityProvider() }
    val chatStore = remember { ChatStore(appContext) }
    val initialWorkspace = remember { chatStore.load() }
    val sessions = remember { mutableStateListOf<ChatSession>().apply { addAll(initialWorkspace.sessions) } }
    var activeSessionId by remember { mutableStateOf(initialWorkspace.activeSessionId) }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    fun activeSession(): ChatSession = sessions.firstOrNull { it.id == activeSessionId }
        ?: sessions.first()

    fun loadActiveMessages() {
        messages.clear()
        messages.addAll(activeSession().messages)
    }

    fun syncActiveSession() {
        val session = activeSession()
        session.messages.clear()
        session.messages.addAll(messages.filter { it.text.isNotEmpty() && it.text != "…" })
        session.updatedAt = System.currentTimeMillis()
        if (session.title == "Nova conversa") {
            val firstUser = session.messages.firstOrNull { it.role == "Você" }?.text?.trim()
            if (!firstUser.isNullOrBlank()) session.title = firstUser.take(36)
        }
    }

    fun saveWorkspace() {
        syncActiveSession()
        chatStore.save(ChatWorkspace(sessions.toMutableList(), activeSessionId))
    }

    LaunchedEffect(Unit) { loadActiveMessages() }

    val engine = remember { AiChat.getInferenceEngine(appContext) }
    val memory: MemoryGateway = remember {
        try { NativeMemoryGateway(appContext) } catch (_: Throwable) { UnavailableMemoryGateway }
    }

    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Offline • inicializando motor local") }
    var modelName by remember { mutableStateOf(prefs.getString(PREF_MODEL_NAME, null)) }
    var modelReady by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<Job?>(null) }
    var modelDownloadJob by remember { mutableStateOf<Job?>(null) }
    var modelDownloadState by remember { mutableStateOf<ModelDownloadState>(ModelDownloadState.Idle) }
    var modelProbe by remember { mutableStateOf<GgufProbe?>(null) }
    var lastMemoryStatus by remember { mutableStateOf(if (memory.available) MemoryStatus.MISS else MemoryStatus.UNAVAILABLE) }
    var lastMemoryIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastTrajectoryUsed by remember { mutableStateOf(false) }
    var lastWindowCount by remember { mutableIntStateOf(0) }
    var pendingMemoryExport by remember { mutableStateOf<String?>(null) }

    DisposableEffect(memory, modelDownloader) {
        onDispose {
            modelDownloader.cancel()
            runCatching { saveWorkspace() }
            if (memory is NativeMemoryGateway) memory.close()
        }
    }

    fun clearLastMemoryStatus() {
        lastMemoryStatus = if (memory.available) MemoryStatus.MISS else MemoryStatus.UNAVAILABLE
        lastMemoryIds = emptyList()
        lastTrajectoryUsed = false
        lastWindowCount = 0
    }

    suspend fun activateModel(file: File, displayName: String) {
        modelReady = false
        modelProbe = loadLocalModel(engine, file)
        modelName = displayName
        prefs.edit()
            .putString(PREF_MODEL_PATH, file.absolutePath)
            .putString(PREF_MODEL_NAME, displayName)
            .apply()
        modelReady = true
    }

    fun startDefaultModelDownload() {
        if (modelDownloadJob?.isActive == true || busy) return
        val descriptor = ModelCatalog.defaultModel

        val installedDefault = modelManager.installedModels()
            .firstOrNull { it.valid && it.name == descriptor.fileName }
        if (installedDefault != null) {
            modelDownloadState = ModelDownloadState.Ready(installedDefault.path)
            modelDownloadJob = scope.launch {
                busy = true
                status = "Local • carregando ${descriptor.displayName}…"
                try {
                    activateModel(File(installedDefault.path), descriptor.displayName)
                    status = "Offline • ${descriptor.displayName} pronto"
                } catch (e: Exception) {
                    modelReady = false
                    status = "Erro no modelo • ${e.message ?: e.javaClass.simpleName}"
                } finally {
                    busy = false
                    modelDownloadJob = null
                }
            }
            return
        }

        val settings = settingsStore.load()
        val network = currentNetworkState(appContext)
        if (!network.connected || !network.validated) {
            modelDownloadState = ModelDownloadState.Failed("Sem conexão com Internet validada")
            status = "Sem modelo • conecte à Internet ou importe um GGUF"
            return
        }
        if (settings.wifiOnlyModelDownloads && !network.wifi) {
            modelDownloadState = ModelDownloadState.Failed("Aguardando Wi-Fi")
            status = "Sem modelo • download configurado para aguardar Wi-Fi"
            return
        }

        modelDownloadJob = scope.launch {
            busy = true
            status = "Online • iniciando download de ${descriptor.displayName}…"
            val result = modelDownloader.download(descriptor) { next ->
                scope.launch {
                    modelDownloadState = next
                    if (next is ModelDownloadState.Downloading || next is ModelDownloadState.Verifying) {
                        status = downloadStatusText(next, descriptor)
                    }
                }
            }

            try {
                modelDownloadState = result
                when (result) {
                    is ModelDownloadState.Ready -> {
                        status = "Local • download validado; carregando modelo…"
                        activateModel(File(result.localPath), descriptor.displayName)
                        status = "Offline • ${descriptor.displayName} pronto"
                    }
                    is ModelDownloadState.Failed -> {
                        status = "Falha no download • ${result.message}"
                    }
                    ModelDownloadState.Cancelled -> {
                        status = "Download do modelo cancelado"
                    }
                    else -> {
                        status = downloadStatusText(result, descriptor)
                    }
                }
            } catch (e: Exception) {
                modelReady = false
                status = "Erro ao carregar modelo baixado • ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
                modelDownloadJob = null
            }
        }
    }

    LaunchedEffect(Unit) {
        val savedFile = prefs.getString(PREF_MODEL_PATH, null)?.let(::File)
        if (savedFile != null && savedFile.isFile) {
            busy = true
            try {
                activateModel(savedFile, modelName ?: savedFile.name)
                status = "Offline • ${modelName ?: savedFile.name} pronto"
            } catch (e: Exception) {
                modelReady = false
                status = "Erro no modelo • ${e.message ?: e.javaClass.simpleName}"
            } finally { busy = false }
            return@LaunchedEffect
        }

        val installed = modelManager.installedModels().firstOrNull { it.valid }
        if (installed != null) {
            busy = true
            try {
                activateModel(File(installed.path), installed.name)
                status = "Offline • ${installed.name} pronto"
            } catch (e: Exception) {
                modelReady = false
                status = "Erro no modelo • ${e.message ?: e.javaClass.simpleName}"
            } finally { busy = false }
            return@LaunchedEffect
        }

        if (settingsStore.load().autoDownloadDefaultModel) {
            startDefaultModelDownload()
        } else {
            status = "Sem modelo • baixe o modelo padrão ou importe um GGUF"
        }
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = displayNameForUri(context, uri)
        if (name == null || !name.endsWith(".gguf", ignoreCase = true)) {
            status = "Arquivo inválido • selecione um .gguf"
            return@rememberLauncherForActivityResult
        }
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) {}
        scope.launch {
            busy = true
            modelDownloadState = ModelDownloadState.Idle
            status = "Importando e validando $name…"
            try {
                val (localFile, importedProbe) = importModel(context, uri, name)
                modelProbe = importedProbe
                activateModel(localFile, name)
                status = "Offline • $name pronto"
            } catch (e: Exception) {
                modelReady = false
                status = "Erro no modelo • ${e.message ?: e.javaClass.simpleName}"
            } finally { busy = false }
        }
    }

    val memoryExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val snapshot = pendingMemoryExport
        pendingMemoryExport = null
        if (uri == null || snapshot == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Salvando exportação da Memoria.ia…"
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w").use { output ->
                        requireNotNull(output) { "Não foi possível abrir o arquivo de destino" }
                        output.write(snapshot.toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                }
                status = "Exportação da Memoria.ia salva"
            } catch (e: Exception) {
                status = "Erro ao salvar memória • ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun regenerateResponse(responseId: String) {
        if (busy || !modelReady) return
        val responseIndex = messages.indexOfFirst { it.id == responseId }
        if (responseIndex < 0) return
        val userIndex = (responseIndex - 1 downTo 0).firstOrNull { messages[it].role == "Você" } ?: return
        val userText = messages[userIndex].text
        val previous = messages[responseIndex]
        if (previous.generation?.source == ResponseSource.CURIOSITY) return
        val auditedMemory = previous.memory?.copy(learnedMemoryIds = emptyList())
        auditedMemory?.let {
            lastMemoryStatus = it.status
            lastMemoryIds = it.memoryIds
            lastTrajectoryUsed = it.trajectoryUsed
            lastWindowCount = it.conversationWindowCount
        }

        messages[responseIndex] = previous.copy(
            text = "…",
            memory = auditedMemory,
            generation = GenerationMetadata(source = ResponseSource.LOCAL, modelName = modelName),
        )

        generationJob = scope.launch {
            busy = true
            generating = true
            status = "Offline • regenerando localmente…"
            val answer = StringBuilder()
            try {
                val prompt = materializePrompt(userText, auditedMemory)
                val generationStartedAt = System.currentTimeMillis()
                engine.sendUserPrompt(prompt, predictLength = 512).collect { token ->
                    answer.append(token)
                    messages[responseIndex] = messages[responseIndex].copy(text = answer.toString())
                }
                val generationLatency = System.currentTimeMillis() - generationStartedAt
                messages[responseIndex] = messages[responseIndex].copy(
                    text = answer.toString().ifBlank { "O modelo não gerou resposta." },
                    generation = messages[responseIndex].generation?.copy(latencyMs = generationLatency),
                )
                saveWorkspace()
                status = "Offline • ${modelName ?: "GGUF"} pronto"
            } catch (_: CancellationException) {
                if (answer.isEmpty()) messages[responseIndex] = previous
                saveWorkspace()
                status = "Offline • geração interrompida"
            } catch (e: Exception) {
                messages[responseIndex] = previous
                saveWorkspace()
                status = "Erro ao regenerar • ${e.message ?: e.javaClass.simpleName}"
            } finally {
                generating = false
                generationJob = null
                busy = false
            }
        }
    }

    fun runCuriosity(responseId: String) {
        if (busy || !modelReady || !curiosityProvider.available) return
        val responseIndex = messages.indexOfFirst { it.id == responseId }
        if (responseIndex < 0) return
        val localResponse = messages[responseIndex]
        if (localResponse.generation?.source == ResponseSource.CURIOSITY) return
        val userIndex = (responseIndex - 1 downTo 0).firstOrNull { messages[it].role == "Você" } ?: return
        val userQuestion = messages[userIndex].text
        val settings = settingsStore.load()

        if (settings.blockNetworkAfterModelDownload) {
            status = "Curiosidade bloqueada pela preferência de rede"
            return
        }
        val network = currentNetworkState(appContext)
        if (!network.connected || !network.validated) {
            status = "Curiosidade indisponível • sem Internet validada"
            return
        }

        val curiosityMessage = ChatMessage(
            role = "OFF.IA",
            text = "…",
            generation = GenerationMetadata(
                source = ResponseSource.CURIOSITY,
                modelName = modelName,
            ),
        )
        messages += curiosityMessage
        val curiosityIndex = messages.lastIndex
        saveWorkspace()

        generationJob = scope.launch {
            busy = true
            status = "Online • buscando fontes públicas…"
            val answer = StringBuilder()
            try {
                val result = curiosityProvider.acquire(
                    CuriosityRequest(
                        userQuestion = userQuestion,
                        localAnswer = localResponse.text,
                        maxSources = 3,
                    ),
                )
                messages[curiosityIndex] = messages[curiosityIndex].copy(
                    generation = messages[curiosityIndex].generation?.copy(publicSources = result.sources),
                )

                status = "Online • registrando fontes públicas na Memoria.ia…"
                val publicContext = learnAndResolveCuriosity(
                    memory = memory,
                    result = result,
                    userQuestion = userQuestion,
                    sessionId = activeSessionId,
                    requestId = curiosityMessage.id,
                )
                val publicLearning = publicContext.learning
                saveWorkspace()

                if (!publicContext.readyForRendering) {
                    val detail = publicLearning.failureReason?.take(180)
                    messages[curiosityIndex] = messages[curiosityIndex].copy(
                        text = when {
                            publicLearning.flushFailed ->
                                "Curiosidade encontrou fontes, mas a Memoria.ia não confirmou a persistência pública. O modelo local não foi chamado."
                            !detail.isNullOrBlank() ->
                                "Curiosidade encontrou fontes, mas a Memoria.ia rejeitou evidência pública: $detail"
                            publicLearning.learned ->
                                "As fontes foram memorizadas, mas a Memoria.ia não selecionou contexto público confiável para esta pergunta. O modelo local não foi chamado."
                            else ->
                                "Curiosidade encontrou fontes, mas nem todas foram aceitas pela Memoria.ia. O modelo local não foi chamado."
                        },
                    )
                    saveWorkspace()
                    status = when {
                        publicLearning.flushFailed ->
                            "Online • Curiosidade interrompida • flush da memória pública falhou"
                        !detail.isNullOrBlank() ->
                            "Online • Curiosidade • $detail"
                        publicLearning.learned ->
                            "Offline • Curiosidade interrompida • contexto público não resolvido"
                        else ->
                            "Online • Curiosidade interrompida • memória pública incompleta"
                    }
                    return@launch
                }

                status = "Offline • Memoria.ia resolveu contexto público • gerando resposta final…"
                generating = true
                val generationStartedAt = System.currentTimeMillis()
                val prompt = materializeResolvedCuriosityPrompt(userQuestion, publicContext.resolution)
                engine.sendUserPrompt(prompt, predictLength = 512).collect { token ->
                    answer.append(token)
                    messages[curiosityIndex] = messages[curiosityIndex].copy(text = answer.toString())
                }
                val generationLatency = System.currentTimeMillis() - generationStartedAt
                messages[curiosityIndex] = messages[curiosityIndex].copy(
                    text = answer.toString().ifBlank { "As fontes foram memorizadas, mas o modelo não gerou uma resposta final." },
                    generation = messages[curiosityIndex].generation?.copy(
                        latencyMs = generationLatency,
                        publicSources = result.sources,
                    ),
                )
                saveWorkspace()
                status = "Offline • Curiosidade concluída • ${result.sources.size} fonte(s) memorizada(s) • ${publicLearning.storedMemoryIds.size} memória(s) pública(s)"
            } catch (_: CancellationException) {
                if (answer.isEmpty()) {
                    messages[curiosityIndex] = messages[curiosityIndex].copy(text = "Curiosidade interrompida.")
                }
                saveWorkspace()
                status = "Offline • Curiosidade interrompida"
            } catch (e: Exception) {
                messages[curiosityIndex] = messages[curiosityIndex].copy(
                    text = "Curiosidade indisponível: ${e.message ?: e.javaClass.simpleName}",
                )
                saveWorkspace()
                status = "Erro na Curiosidade"
            } finally {
                generating = false
                generationJob = null
                busy = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ConversationTopBar(
                status = status,
                modelSummary = modelProbe?.let {
                    buildString {
                        append("${modelName ?: "GGUF"} • GGUF v${it.version} • ${it.sizeBytes / (1024 * 1024)} MB")
                        it.runtimeProfile?.let { profile ->
                            append("\nTemplate: ${profile.displayLabel}")
                        }
                    }
                },
                activeSession = activeSession(),
                sessions = sessions,
                busy = busy,
                memoryAvailable = memory.available,
                modelDownloadState = modelDownloadState,
                onSelectSession = { sessionId ->
                    saveWorkspace()
                    activeSessionId = sessionId
                    loadActiveMessages()
                    clearLastMemoryStatus()
                    chatStore.save(ChatWorkspace(sessions.toMutableList(), activeSessionId))
                },
                onNewConversation = {
                    saveWorkspace()
                    val newSession = chatStore.newSession()
                    sessions += newSession
                    activeSessionId = newSession.id
                    messages.clear()
                    clearLastMemoryStatus()
                    chatStore.save(ChatWorkspace(sessions.toMutableList(), activeSessionId))
                },
                onRenameConversation = { newTitle ->
                    activeSession().title = newTitle
                    activeSession().updatedAt = System.currentTimeMillis()
                    chatStore.save(ChatWorkspace(sessions.toMutableList(), activeSessionId))
                    status = "Conversa renomeada"
                },
                onDeleteConversation = {
                    val deletingId = activeSessionId
                    sessions.removeAll { it.id == deletingId }
                    if (sessions.isEmpty()) sessions += chatStore.newSession()
                    activeSessionId = sessions.maxByOrNull { it.updatedAt }?.id ?: sessions.first().id
                    loadActiveMessages()
                    clearLastMemoryStatus()
                    chatStore.save(ChatWorkspace(sessions.toMutableList(), activeSessionId))
                    status = "Conversa excluída"
                },
                onChooseModel = { modelPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                onDownloadDefaultModel = { startDefaultModelDownload() },
                onCancelModelDownload = {
                    modelDownloader.cancel()
                    status = "Online • cancelando download do modelo…"
                },
                onExportMemory = {
                    scope.launch {
                        busy = true
                        status = "Preparando exportação da Memoria.ia…"
                        try {
                            memory.flush()
                            pendingMemoryExport = collectFullMemorySnapshot(memory)
                            memoryExportPicker.launch("offia-memoria-${System.currentTimeMillis()}.json")
                            status = "Exportação pronta para salvar"
                        } catch (e: Exception) {
                            pendingMemoryExport = null
                            status = "Erro ao exportar memória • ${e.message ?: e.javaClass.simpleName}"
                        } finally {
                            busy = false
                        }
                    }
                },
                onCopiedConversation = { status = "Conversa copiada" },
            )
        },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                val memoryLabel = when (lastMemoryStatus) {
                    MemoryStatus.HIT -> "HIT"
                    MemoryStatus.MISS -> "MISS"
                    MemoryStatus.UNRESOLVED -> "UNRESOLVED"
                    MemoryStatus.UNAVAILABLE -> "indisponível"
                }
                val idsLabel = if (lastMemoryIds.isEmpty()) "" else " • ids=${lastMemoryIds.joinToString()}"
                val trajectoryLabel = if (lastTrajectoryUsed) " • trajetória=$lastWindowCount" else ""
                Text(
                    "Memoria: $memoryLabel$idsLabel$trajectoryLabel • Inferência: ${if (modelReady) "llama.cpp local" else "aguardando modelo"}",
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Digite uma mensagem…") },
                        maxLines = 4,
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = generating || (input.isNotBlank() && modelReady && !busy),
                        modifier = Modifier.heightIn(min = 56.dp),
                        onClick = {
                            if (generating) {
                                status = "Offline • interrompendo geração…"
                                generationJob?.cancel()
                                return@Button
                            }

                            val text = input.trim()
                            val sessionIdForResolve = activeSessionId
                            val structuralSequence = messages.count { it.role == "Você" }.toLong()
                            val runtimeSettings = settingsStore.load()
                            val structuralBinding = if (
                                runtimeSettings.laboratoryMode &&
                                !runtimeSettings.blockNetworkAfterModelDownload
                            ) {
                                runCatching { structuralIdentity.loadBinding() }.getOrNull()
                            } else {
                                null
                            }
                            val structuralClient = structuralBinding?.let {
                                MemoriaServerStructuralClient(it.serverBaseUrl, structuralTokenProvider)
                            }
                            val trajectoryWindow = messages
                                .asSequence()
                                .filter { it.text.isNotBlank() && it.text != "…" }
                                .takeLastCompat(ACTIVE_TRAJECTORY_TURNS)
                                .mapIndexed { index, message ->
                                    MemoryWindowTurn(
                                        role = if (message.role == "Você") "user" else "assistant",
                                        text = message.text,
                                        order = (index + 1).toLong(),
                                    )
                                }
                                .toList()
                            input = ""
                            messages += ChatMessage(
                                role = "Você",
                                text = text,
                                generation = GenerationMetadata(source = ResponseSource.USER),
                            )
                            messages += ChatMessage(
                                role = "OFF.IA",
                                text = "…",
                                generation = GenerationMetadata(source = ResponseSource.LOCAL, modelName = modelName),
                            )
                            val responseIndex = messages.lastIndex
                            generationJob = scope.launch {
                                busy = true
                                saveWorkspace()
                                status = "Offline • consultando memória local…"
                                try {
                                    val localResolution = memory.resolve(text, sessionIdForResolve, trajectoryWindow)
                                    var structuralResolutionStatus: MemoryStatus? = null
                                    val structuralResolution = if (structuralClient != null) {
                                        status = "Híbrido • consultando memória estrutural V2…"
                                        runCatching {
                                            structuralClient.resolve(text).toMemoryResolution()
                                        }.getOrNull().also {
                                            structuralResolutionStatus = it?.status
                                        }
                                    } else {
                                        null
                                    }
                                    val resolution = selectLaboratoryMemoryResolution(
                                        local = localResolution,
                                        structural = structuralResolution,
                                    )

                                    lastMemoryStatus = resolution.status
                                    lastMemoryIds = resolution.memoryIds
                                    lastTrajectoryUsed = resolution.trajectoryUsed
                                    lastWindowCount = resolution.conversationWindowCount

                                    val responseMemory = ResponseMemoryMetadata(
                                        status = resolution.status,
                                        memoryIds = resolution.memoryIds,
                                        confidence = resolution.confidence,
                                        selectedContext = resolution.contextItems.joinToString("\n"),
                                        trajectoryUsed = resolution.trajectoryUsed,
                                        conversationWindowCount = resolution.conversationWindowCount,
                                    )
                                    messages[responseIndex] = messages[responseIndex].copy(memory = responseMemory)

                                    val prompt = materializePrompt(text, resolution)
                                    status = "Offline • gerando localmente…"
                                    generating = true
                                    val generationStartedAt = System.currentTimeMillis()
                                    val answer = StringBuilder()
                                    engine.sendUserPrompt(prompt, predictLength = 512).collect { token ->
                                        answer.append(token)
                                        messages[responseIndex] = messages[responseIndex].copy(text = answer.toString())
                                    }
                                    generating = false
                                    val generationLatency = System.currentTimeMillis() - generationStartedAt
                                    messages[responseIndex] = messages[responseIndex].copy(
                                        generation = messages[responseIndex].generation?.copy(latencyMs = generationLatency),
                                    )

                                    var structuralObserved = false
                                    if (answer.isEmpty()) {
                                        messages[responseIndex] = messages[responseIndex].copy(text = "O modelo não gerou resposta.")
                                    } else {
                                        if (memory.available) {
                                            status = "Offline • aprendendo turno…"
                                            val learned = memory.learnTurn(text, answer.toString())
                                            memory.flush()
                                            if (learned.memoryIds.isNotEmpty()) {
                                                val currentMemory = messages[responseIndex].memory
                                                if (currentMemory != null) {
                                                    messages[responseIndex] = messages[responseIndex].copy(
                                                        memory = currentMemory.copy(learnedMemoryIds = learned.memoryIds.distinct()),
                                                    )
                                                }
                                            }
                                        }

                                        // Structural V2 ordering invariant:
                                        // resolve against the past first, then observe only the
                                        // user's completed turn. Assistant/LLM text never enters
                                        // this structural trail.
                                        if (structuralClient != null) {
                                            status = "Híbrido • registrando texto do usuário na memória estrutural V2…"
                                            structuralObserved = runCatching {
                                                structuralClient.observeUserText(
                                                    text = text,
                                                    sequence = structuralSequence,
                                                    sessionId = sessionIdForResolve,
                                                )
                                            }.isSuccess
                                        }
                                    }
                                    saveWorkspace()
                                    status = when {
                                        structuralClient != null && structuralObserved ->
                                            "Híbrido • ${modelName ?: "GGUF"} • estrutural V2 sincronizada"
                                        structuralClient != null && structuralResolutionStatus == MemoryStatus.HIT ->
                                            "Híbrido • ${modelName ?: "GGUF"} • estrutural V2 HIT • sync pendente"
                                        else ->
                                            "Offline • ${modelName ?: "GGUF"} pronto"
                                    }
                                } catch (_: CancellationException) {
                                    if (messages[responseIndex].text == "…") {
                                        messages[responseIndex] = messages[responseIndex].copy(text = "Geração interrompida.")
                                    }
                                    saveWorkspace()
                                    status = "Offline • geração interrompida"
                                } catch (e: Exception) {
                                    messages[responseIndex] = messages[responseIndex].copy(
                                        text = "Erro local: ${e.message ?: e.javaClass.simpleName}",
                                    )
                                    saveWorkspace()
                                    status = "Erro no ciclo local"
                                } finally {
                                    generating = false
                                    generationJob = null
                                    busy = false
                                }
                            }
                        },
                    ) { Text(if (generating) "Parar" else "Enviar") }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                MessageCard(
                    message = message,
                    busy = busy,
                    onRegenerate = if (modelReady) ({ responseId -> regenerateResponse(responseId) }) else null,
                    onCuriosity = if (modelReady && curiosityProvider.available) ({ responseId -> runCuriosity(responseId) }) else null,
                )
            }
        }
    }
}

private fun <T> Sequence<T>.takeLastCompat(limit: Int): Sequence<T> {
    val buffer = ArrayDeque<T>(limit)
    for (item in this) {
        if (buffer.size == limit) buffer.removeFirst()
        buffer.addLast(item)
    }
    return buffer.asSequence()
}
