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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufProbe(val sizeBytes: Long, val version: Int)

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
        engine.setSystemPrompt(SYSTEM_PROMPT)
    } catch (e: Exception) {
        throw IOException(modelLoadMessage(e, probe), e)
    }
    return probe
}

@Composable
fun OffiaChatScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val prefs = remember { appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val chatStore = remember { ChatStore(appContext) }
    val initialWorkspace = remember { chatStore.load() }
    val sessions = remember { mutableStateListOf<ChatSession>().apply { addAll(initialWorkspace.sessions) } }
    var activeSessionId by remember { mutableStateOf(initialWorkspace.activeSessionId) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var sessionMenuExpanded by remember { mutableStateOf(false) }

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
    DisposableEffect(memory) {
        onDispose {
            runCatching { saveWorkspace() }
            if (memory is NativeMemoryGateway) memory.close()
        }
    }

    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Offline • inicializando motor local") }
    var modelName by remember { mutableStateOf(prefs.getString(PREF_MODEL_NAME, null)) }
    var modelReady by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var modelProbe by remember { mutableStateOf<GgufProbe?>(null) }
    var lastMemoryStatus by remember { mutableStateOf(if (memory.available) MemoryStatus.MISS else MemoryStatus.UNAVAILABLE) }
    var lastMemoryIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastTrajectoryUsed by remember { mutableStateOf(false) }
    var lastWindowCount by remember { mutableIntStateOf(0) }
    var pendingMemoryExport by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val savedFile = prefs.getString(PREF_MODEL_PATH, null)?.let(::File)
        if (savedFile != null && savedFile.isFile) {
            busy = true
            try {
                modelProbe = loadLocalModel(engine, savedFile)
                modelReady = true
                status = "Offline • ${modelName ?: "GGUF"} pronto"
            } catch (e: Exception) {
                status = "Erro no modelo • ${e.message ?: e.javaClass.simpleName}"
            } finally { busy = false }
        } else status = "Offline • selecione um modelo GGUF"
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
            modelReady = false
            status = "Importando e validando $name…"
            try {
                val (localFile, importedProbe) = importModel(context, uri, name)
                modelProbe = importedProbe
                loadLocalModel(engine, localFile)
                modelName = name
                prefs.edit().putString(PREF_MODEL_PATH, localFile.absolutePath).putString(PREF_MODEL_NAME, name).apply()
                modelReady = true
                status = "Offline • $name pronto"
            } catch (e: Exception) {
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

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("OFF.IA", style = MaterialTheme.typography.headlineMedium)
                Text(status, style = MaterialTheme.typography.bodySmall)
                modelProbe?.let {
                    Text("GGUF v${it.version} • ${it.sizeBytes / (1024 * 1024)} MB", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(enabled = !busy, onClick = { sessionMenuExpanded = true }) {
                            Text(activeSession().title.take(18))
                        }
                        DropdownMenu(expanded = sessionMenuExpanded, onDismissRequest = { sessionMenuExpanded = false }) {
                            sessions.sortedByDescending { it.updatedAt }.forEach { session ->
                                DropdownMenuItem(
                                    text = { Text(session.title) },
                                    onClick = {
                                        saveWorkspace()
                                        activeSessionId = session.id
                                        loadActiveMessages()
                                        lastMemoryStatus = if (memory.available) MemoryStatus.MISS else MemoryStatus.UNAVAILABLE
                                        lastMemoryIds = emptyList()
                                        lastTrajectoryUsed = false
                                        lastWindowCount = 0
                                        sessionMenuExpanded = false
                                        chatStore.save(ChatWorkspace(sessions.toMutableList(), activeSessionId))
                                    },
                                )
                            }
                        }
                    }
                    OutlinedButton(enabled = !busy, onClick = {
                        saveWorkspace()
                        val newSession = chatStore.newSession()
                        sessions += newSession
                        activeSessionId = newSession.id
                        messages.clear()
                        lastMemoryStatus = if (memory.available) MemoryStatus.MISS else MemoryStatus.UNAVAILABLE
                        lastMemoryIds = emptyList()
                        lastTrajectoryUsed = false
                        lastWindowCount = 0
                        chatStore.save(ChatWorkspace(sessions.toMutableList(), activeSessionId))
                    }) { Text("Nova") }
                    OutlinedButton(enabled = !busy, onClick = { modelPicker.launch(arrayOf("application/octet-stream", "*/*")) }) {
                        Text(if (modelName == null) "Modelo" else "Trocar")
                    }
                }
                TextButton(
                    enabled = memory.available && !busy,
                    onClick = {
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
                ) { Text("Exportar Memoria.ia") }
            }
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
                        enabled = input.isNotBlank() && modelReady && !busy,
                        modifier = Modifier.heightIn(min = 56.dp),
                        onClick = {
                            val text = input.trim()
                            val sessionIdForResolve = activeSessionId
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
                            scope.launch {
                                busy = true
                                saveWorkspace()
                                status = "Offline • consultando memória local…"
                                try {
                                    val resolution = memory.resolve(text, sessionIdForResolve, trajectoryWindow)
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
                                    val generationStartedAt = System.currentTimeMillis()
                                    val answer = StringBuilder()
                                    engine.sendUserPrompt(prompt, predictLength = 512).collect { token ->
                                        answer.append(token)
                                        messages[responseIndex] = messages[responseIndex].copy(text = answer.toString())
                                    }
                                    val generationLatency = System.currentTimeMillis() - generationStartedAt
                                    messages[responseIndex] = messages[responseIndex].copy(
                                        generation = messages[responseIndex].generation?.copy(latencyMs = generationLatency),
                                    )

                                    if (answer.isEmpty()) {
                                        messages[responseIndex] = messages[responseIndex].copy(text = "O modelo não gerou resposta.")
                                    } else if (memory.available) {
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
                                    saveWorkspace()
                                    status = "Offline • ${modelName ?: "GGUF"} pronto"
                                } catch (e: Exception) {
                                    messages[responseIndex] = messages[responseIndex].copy(
                                        text = "Erro local: ${e.message ?: e.javaClass.simpleName}",
                                    )
                                    saveWorkspace()
                                    status = "Erro no ciclo local"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) { Text("Enviar") }
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
                MessageCard(message)
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
