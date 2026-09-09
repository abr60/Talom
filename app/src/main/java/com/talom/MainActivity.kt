package com.talom

import android.Manifest
import android.accounts.AccountManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.talom.core.ai.AcademicExtractionService
import com.talom.core.ai.AiMode
import com.talom.core.ai.AiPreferences
import com.talom.core.ai.AiPrompt
import com.talom.core.ai.AiProviderConfig
import com.talom.core.ai.AiProviderResult
import com.talom.core.ai.CloudErrorMapper
import com.talom.core.ai.ConfiguredAiProvider
import com.talom.core.ai.ConnectionStatus
import com.talom.core.ai.GeminiAiProvider
import com.talom.core.ai.ModelRecommender
import com.talom.core.ai.OllamaAiProvider
import com.talom.core.ai.OpenAiCompatibleAiProvider
import com.talom.core.auth.GoogleAccountTokenProvider
import com.talom.core.source.SourceMessage
import com.talom.data.TalomDatabaseProvider
import com.talom.data.academic.AcademicItemRepository
import com.talom.data.academic.ConversationInsightRepository
import com.talom.data.classroom.ClassroomRepository
import com.talom.data.whatsapp.WhatsAppDirectoryEntity
import com.talom.data.whatsapp.WhatsAppWhitelist
import com.talom.source.whatsapp.RootWhatsAppDirectoryProvider
import com.talom.source.whatsapp.RootWhatsAppSnapshotProvider
import com.talom.source.whatsapp.WhatsAppConversation
import com.talom.source.whatsapp.WhatsAppExportParser
import com.talom.source.whatsapp.WhatsAppMessageSource
import com.talom.source.whatsapp.WhatsAppPullRepository
import com.talom.source.whatsapp.WhatsAppPullWorker
import com.talom.source.whatsapp.TalomWorkScheduler
import com.talom.source.classroom.GoogleClassroomReadOnlyProvider
import com.talom.data.whatsapp.RelationCategory
import com.talom.ui.academic.AcademicScreen
import com.talom.ui.personal.PersonalScreen
import com.talom.ui.settings.AiSettingsScreen
import com.talom.ui.settings.ClassroomSettingsScreen
import com.talom.ui.settings.ManageConversationsScreen
import com.talom.ui.settings.SettingsHubScreen
import com.talom.ui.settings.SettingsNavGraph
import com.talom.ui.settings.WhatsAppSettingsScreen
import com.talom.ui.theme.TalomTheme
import com.talom.ui.theme.TalomThemeMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class TalomDestination(
    val label: String,
    val icon: ImageVector,
)

private val Destinations = listOf(
    TalomDestination("Academic", Icons.AutoMirrored.Filled.List),
    TalomDestination("Personal", Icons.Filled.Person),
    TalomDestination("Settings", Icons.Filled.Settings),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val database = TalomDatabaseProvider.get(this)
        val aiPreferences = AiPreferences(this)
        val aiConfig = aiPreferences.config()
        val aiService = if (
            (aiConfig.mode == AiMode.CLOUD &&
                aiPreferences.cloudConsent() &&
                !aiPreferences.apiKey().isNullOrBlank()) ||
            (aiConfig.mode == AiMode.LOCAL && aiPreferences.localConsent())
        ) {
            AcademicExtractionService(
                ConfiguredAiProvider.create(aiConfig, aiPreferences.apiKey()),
            )
        } else {
            null
        }

        val talomPreferences = com.talom.core.TalomPreferences(this)
        TalomWorkScheduler.scheduleDailyAt(
            this,
            hour = talomPreferences.pullHour(),
            minute = talomPreferences.pullMinute(),
        )
        val pullRepository = WhatsAppPullRepository(
            source = WhatsAppMessageSource(RootWhatsAppSnapshotProvider(this)),
            whitelistDao = database.whatsappWhitelistDao(),
            cursorDao = database.whatsappCursorDao(),
            messageDao = database.whatsappMessageDao(),
            statusDao = database.sourceStatusDao(),
            pullLogDao = database.pullLogDao(),
            database = database,
            academicExtractionService = aiService,
            academicItemRepository = AcademicItemRepository(database.academicItemDao()),
            conversationInsightRepository = ConversationInsightRepository(database.conversationInsightDao()),
            messageWindowDays = { AiPreferences(this).messageWindowDays() },
        )
        setContent {
            TalomApp(
                database = database,
                pullRepository = pullRepository,
                academicExtractionService = aiService,
                academicItemRepository = AcademicItemRepository(database.academicItemDao()),
                conversationInsightRepository = ConversationInsightRepository(database.conversationInsightDao()),
            )
        }
    }
}

@Composable
private fun TalomApp(
    database: com.talom.data.TalomDatabase,
    pullRepository: WhatsAppPullRepository,
    academicExtractionService: AcademicExtractionService?,
    academicItemRepository: AcademicItemRepository,
    conversationInsightRepository: ConversationInsightRepository,
) {
    val whitelistDao = database.whatsappWhitelistDao()
    val whitelist by whitelistDao.observeAll().collectAsState(initial = emptyList())
    val pullLog by database.pullLogDao().observeRecent("whatsapp").collectAsState(initial = emptyList())
    val latestPullLog = pullLog.firstOrNull()
    val academicItems by database.academicItemDao().observeAll().collectAsState(initial = emptyList())
    val insights by database.conversationInsightDao().observeAll().collectAsState(initial = emptyList())
    val fromMeKeysList by database.whatsappMessageDao().observeFromMeKeys().collectAsState(initial = emptyList())
    val fromMeKeys = remember(fromMeKeysList) { fromMeKeysList.toSet() }
    val classroomCourses by database.classroomDao().observeCourses().collectAsState(initial = emptyList())
    val classroomCoursework by database.classroomDao().observeCoursework().collectAsState(initial = emptyList())
    val classroomAnnouncements by database.classroomDao().observeAnnouncements().collectAsState(initial = emptyList())
    val classroomStatus by database.sourceStatusDao()
        .observe("google-classroom")
        .collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var settingsTabReselected by remember { mutableStateOf(false) }
    val cachedDirectoryEntities by database.whatsappDirectoryDao().observeAll().collectAsState(initial = emptyList())
    val directory = cachedDirectoryEntities.map { WhatsAppConversation(jid = it.jid, label = it.label, isGroup = it.isGroup) }
    var directoryState by remember { mutableStateOf("Load WhatsApp contacts and groups to choose from them.") }
    var directoryLoading by remember { mutableStateOf(false) }
    var directoryQuery by remember { mutableStateOf("") }
    LaunchedEffect(cachedDirectoryEntities.size) {
        if (cachedDirectoryEntities.isNotEmpty() && directoryState.startsWith("Load WhatsApp")) {
            directoryState = "${cachedDirectoryEntities.size} contacts cached — tap Load to refresh."
        }
    }
    var pullState by remember { mutableStateOf("No pull yet.") }
    var pulling by remember { mutableStateOf(false) }
    var importState by remember { mutableStateOf("No export imported.") }
    val context = LocalContext.current
    var themeMode by remember {
        mutableStateOf(
            runCatching {
                TalomThemeMode.valueOf(
                    context.getSharedPreferences("talom_preferences", android.content.Context.MODE_PRIVATE)
                        .getString("theme_mode", TalomThemeMode.SYSTEM.name)
                        ?: TalomThemeMode.SYSTEM.name,
                )
            }.getOrDefault(TalomThemeMode.SYSTEM),
        )
    }
    val aiPreferences = remember { AiPreferences(context) }
    val talomPreferences = remember { com.talom.core.TalomPreferences(context) }
    val initialAiConfig = remember { aiPreferences.config() }
    var aiMode by remember { mutableStateOf(initialAiConfig.mode) }
    var aiKey by remember { mutableStateOf("") }
    var aiModel by remember {
        mutableStateOf(
            initialAiConfig.modelId ?: if (initialAiConfig.providerId == "openai_compatible") {
                "google/gemma-3-4b-it:free"
            } else {
                "gemini-3.6-flash"
            },
        )
    }
    var cloudConsent by remember { mutableStateOf(aiPreferences.cloudConsent()) }
    var localConsent by remember { mutableStateOf(aiPreferences.localConsent()) }
    var ollamaEndpoint by remember { mutableStateOf(aiPreferences.ollamaEndpoint()) }
    var openAiEndpoint by remember { mutableStateOf(aiPreferences.openAiEndpoint()) }
    var useOpenAiCompatible by remember {
        mutableStateOf(initialAiConfig.providerId == "openai_compatible")
    }
    var pullHour by remember { mutableIntStateOf(talomPreferences.pullHour()) }
    var pullMinute by remember { mutableIntStateOf(talomPreferences.pullMinute()) }
    var fontPreference by remember { mutableStateOf(talomPreferences.fontPreference()) }
    var userIdentity by remember { mutableStateOf(talomPreferences.userIdentity()) }
    var aiSettingsState by remember {
        mutableStateOf(
            com.talom.ui.settings.StatusText.aiConfigSummary(
                aiPreferences.config(),
                aiPreferences.apiKey()?.isNotBlank() == true,
                aiPreferences.cloudConsent(),
                aiPreferences.localConsent(),
            ),
        )
    }
    var localTestState by remember { mutableStateOf("") }
    var messageWindowDays by remember { mutableStateOf(aiPreferences.messageWindowDays()) }
    // Cloud model dropdown: populated from the user's own API key.
    val savedAiKey = remember { aiPreferences.apiKey().orEmpty() }
    var availableModels by remember { mutableStateOf(emptyList<String>()) }
    var modelsLoading by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    var connectionStatus by remember { mutableStateOf<ConnectionStatus?>(null) }
    var modelRecommendationInProgress by remember { mutableStateOf(false) }
    var modelsRefreshKey by remember { mutableIntStateOf(0) }
    // Fix B: when the user switches to OpenAI-compatible and the saved model id
    // doesn't exist in the new provider's model list, clear the model and run
    // the recommender so the user isn't stuck with a stale value.
    LaunchedEffect(useOpenAiCompatible, availableModels.isNotEmpty(), modelsRefreshKey) {
        if (useOpenAiCompatible &&
            availableModels.isNotEmpty() &&
            aiModel.isNotBlank() &&
            aiModel !in availableModels
        ) {
            aiModel = ""
            connectionStatus = null
        }
    }
    LaunchedEffect(aiMode, aiKey, savedAiKey, useOpenAiCompatible, openAiEndpoint, modelsRefreshKey) {
        if (aiMode != AiMode.CLOUD) return@LaunchedEffect
        val key = aiKey.ifBlank { savedAiKey }
        if (key.isBlank()) {
            availableModels = emptyList()
            modelsError = null
            modelsLoading = false
            return@LaunchedEffect
        }
        if (useOpenAiCompatible) {
            val endpoint = openAiEndpoint.trim()
            if (endpoint.isBlank()) {
                availableModels = emptyList()
                modelsError = "Enter an endpoint URL to load models."
                modelsLoading = false
                return@LaunchedEffect
            }
            delay(600)
            modelsLoading = true
            modelsError = null
            OpenAiCompatibleAiProvider(
                AiProviderConfig(
                    mode = AiMode.CLOUD,
                    providerId = "openai_compatible",
                    modelId = null,
                    endpoint = endpoint,
                ),
                key,
            ).listModels()
                .onSuccess { models ->
                    availableModels = models
                    if (models.isEmpty()) modelsError =
                        "Endpoint reachable but /models returned no text-capable models."
                }
                .onFailure {
                    availableModels = emptyList()
                    modelsError = "Models unavailable: ${it.message ?: "unknown error"}"
                }
            modelsLoading = false
            return@LaunchedEffect
        }
        delay(600)
        modelsLoading = true
        modelsError = null
        GeminiAiProvider(
            AiProviderConfig(
                mode = AiMode.CLOUD,
                providerId = "gemini",
                modelId = null,
                endpoint = null,
            ),
            key,
        ).listModels()
            .onSuccess { models ->
                availableModels = models
                if (models.isEmpty()) modelsError = "No generate-capable models returned."
            }
            .onFailure {
                availableModels = emptyList()
                modelsError = "Models unavailable: ${it.message ?: "unknown error"}"
            }
        modelsLoading = false
    }
    var availableLocalModels by remember { mutableStateOf(emptyList<String>()) }
    var localModelsLoading by remember { mutableStateOf(false) }
    var localModelsError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(aiMode, ollamaEndpoint) {
        if (aiMode != AiMode.LOCAL) return@LaunchedEffect
        val endpoint = ollamaEndpoint.trim()
        if (endpoint.isBlank()) {
            availableLocalModels = emptyList()
            localModelsError = null
            localModelsLoading = false
            return@LaunchedEffect
        }
        delay(600)
        localModelsLoading = true
        localModelsError = null
        OllamaAiProvider(
            AiProviderConfig(
                mode = AiMode.LOCAL,
                providerId = "ollama",
                modelId = null,
                endpoint = endpoint,
            ),
        ).listModels()
            .onSuccess { models ->
                availableLocalModels = models
                if (models.isEmpty()) localModelsError = "No models on this Ollama server."
            }
            .onFailure {
                availableLocalModels = emptyList()
                localModelsError = "Models unavailable: ${it.message ?: "unknown error"}"
            }
        localModelsLoading = false
    }
    val loadDirectory: () -> Unit = {
        directoryLoading = true
        directoryState = "Reading WhatsApp contacts and groups..."
        scope.launch {
            RootWhatsAppDirectoryProvider(context).listConversations()
                .onSuccess { contacts ->
                    val now = System.currentTimeMillis()
                    val entities = contacts.map {
                        WhatsAppDirectoryEntity(
                            jid = it.jid,
                            label = it.label,
                            isGroup = it.isGroup,
                            updatedAtMillis = now,
                        )
                    }
                    database.whatsappDirectoryDao().upsertAll(entities)
                    directoryState = "Found ${contacts.size} WhatsApp contacts and groups."
                }
                .onFailure {
                    directoryState = "Could not read WhatsApp contacts: " +
                        (it.message ?: "unknown error")
                }
            directoryLoading = false
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importState = runCatching {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: error("Could not read the selected export.")
                    val result = WhatsAppExportParser.parse(text)
                    if (academicExtractionService == null) {
                        "Imported ${result.messages.size} messages; ignored ${result.ignoredLines} lines."
                    } else {
                        val exportSourceMessages = result.messages.map {
                            SourceMessage(
                                messageId = stableExportMessageId(it.stableId),
                                chatId = 0L,
                                jid = "whatsapp-export",
                                chatSubject = null,
                                fromMe = false,
                                timestampMillis = it.timestampMillis,
                                messageType = 0,
                                text = it.text,
                            )
                        }.filter { !it.text.isNullOrBlank() }
                        val itemCount = AiPrompt.chunkByTokens(exportSourceMessages)
                            .sumOf { batch ->
                                when (val extraction = academicExtractionService.extract(batch)) {
                                    is AiProviderResult.Failure ->
                                        error(extraction.message)
                                    is AiProviderResult.Success -> {
                                        academicItemRepository.persist(extraction.result)
                                        conversationInsightRepository.persist(extraction.result)
                                        extraction.result.items.size
                                    }
                                }
                            }
                        "Imported ${result.messages.size} messages; extracted $itemCount academic items; " +
                            "ignored ${result.ignoredLines} lines."
                    }
                }.getOrElse { "Export import failed: ${it.message ?: "unknown error"}" }
            }
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    // First-launch notification permission request.
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = context.getSharedPreferences("talom_preferences", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("notifications_prompted", false)) {
                prefs.edit().putBoolean("notifications_prompted", true).apply()
                delay(800)
                // Re-check (activity may have been recreated).
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    var classroomAccount by remember {
        mutableStateOf(
            context.getSharedPreferences("talom_preferences", android.content.Context.MODE_PRIVATE)
                .getString("classroom_account", null),
        )
    }
    var classroomSyncing by remember { mutableStateOf(false) }
    val accountPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.let { email ->
                classroomAccount = email
                context.getSharedPreferences("talom_preferences", android.content.Context.MODE_PRIVATE)
                    .edit().putString("classroom_account", email).apply()
            }
        }
    }

    val showNotificationPrompt =
        android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED

    TalomTheme(themeMode = themeMode) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Destinations.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = {
                                if (selectedTab == index && index == 2) {
                                    settingsTabReselected = true
                                }
                                selectedTab = index
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = {
                                Text(
                                    destination.label,
                                    fontWeight = if (selectedTab == index) {
                                        androidx.compose.ui.text.font.FontWeight.Bold
                                    } else {
                                        androidx.compose.ui.text.font.FontWeight.Normal
                                    },
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background,
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Scrollable content bounded between fixed top (status bar via innerPadding)
                    // and fixed bottom NavigationBar (Scaffold bottomBar). Content is clipped
                    // to its bounds so scrolled items pass cleanly underneath fixed elements.
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        ) {
                    when (selectedTab) {
                        0 -> AcademicScreen(
                            academicItems = academicItems,
                            courses = classroomCourses,
                            coursework = classroomCoursework,
                            announcements = classroomAnnouncements,
                            classroomStatus = classroomStatus,
                            formatTime = ::formatTime,
                            onMarkDone = { stableId ->
                                scope.launch {
                                    academicItemRepository.markDone(stableId)
                                }
                            },
                            latestPullLog = latestPullLog,
                        )
                        1 -> {
                            PersonalScreen(
                                insights = insights,
                                whitelist = whitelist,
                                directory = directory,
                                fromMeKeys = fromMeKeys,
                                latestPullLog = latestPullLog,
                            )
                        }
                        else -> {
                            val doSaveAi: () -> Unit = {
                                val activeEndpoint = if (aiMode == AiMode.LOCAL) ollamaEndpoint.trim() else if (useOpenAiCompatible) openAiEndpoint.trim() else ""
                                val missing = com.talom.ui.settings.StatusText.aiMissingFields(
                                    mode = aiMode,
                                    useOpenAiCompatible = useOpenAiCompatible,
                                    endpointBlank = if (aiMode == AiMode.LOCAL || useOpenAiCompatible) activeEndpoint.isBlank() else false,
                                    keyBlank = aiKey.isBlank() && savedAiKey.isBlank(),
                                    modelBlank = aiModel.trim().isBlank(),
                                    cloudConsent = cloudConsent,
                                    localConsent = localConsent,
                                )
                                if (missing != null) {
                                    aiSettingsState = missing
                                } else {
                                    aiPreferences.setConfig(
                                        AiProviderConfig(
                                            mode = aiMode,
                                            providerId = when (aiMode) {
                                                AiMode.CLOUD -> if (useOpenAiCompatible) "openai_compatible" else "gemini"
                                                AiMode.LOCAL -> "ollama"
                                                AiMode.DISABLED -> null
                                            },
                                            modelId = if (aiMode != AiMode.DISABLED) aiModel.trim() else null,
                                            endpoint = when (aiMode) {
                                                AiMode.CLOUD -> if (useOpenAiCompatible) openAiEndpoint.trim() else null
                                                AiMode.LOCAL -> ollamaEndpoint.trim()
                                                AiMode.DISABLED -> null
                                            },
                                        ),
                                    )
                                    runCatching {
                                        if (aiMode == AiMode.CLOUD && aiKey.isNotBlank()) {
                                            aiPreferences.setApiKey(aiKey.trim())
                                            aiKey = ""
                                        }
                                        aiPreferences.setCloudConsent(cloudConsent)
                                        aiPreferences.setLocalConsent(localConsent)
                                    }.onSuccess {
                                        val savedCfg = aiPreferences.config()
                                        aiSettingsState = com.talom.ui.settings.StatusText.aiSaveFeedback(
                                            savedCfg,
                                            aiPreferences.apiKey()?.isNotBlank() == true,
                                            aiPreferences.cloudConsent(),
                                            aiPreferences.localConsent(),
                                        )
                                        (context as? android.app.Activity)?.recreate()
                                    }.onFailure {
                                        aiSettingsState = "Could not secure the API key: ${it.message ?: "unknown error"}"
                                    }
                                }
                            }
                            val doTestOllama: () -> Unit = {
                                localTestState = "Testing Ollama connection..."
                                scope.launch {
                                    OllamaAiProvider(
                                        AiProviderConfig(
                                            mode = AiMode.LOCAL,
                                            providerId = "ollama",
                                            modelId = aiModel.trim(),
                                            endpoint = ollamaEndpoint.trim(),
                                        ),
                                    ).testConnection()
                                        .onSuccess { models ->
                                            localTestState = "Connected. Models: ${models.joinToString()}"
                                        }
                                        .onFailure {
                                            localTestState = "Connection failed: " + (it.message ?: "unknown error")
                                        }
                                }
                            }
                            val doTestConnection: () -> Unit = run@{
                                if (openAiEndpoint.trim().isBlank() ||
                                    (aiKey.isBlank() && savedAiKey.isBlank())
                                ) {
                                    connectionStatus = ConnectionStatus.Failed(
                                        com.talom.core.ai.CloudErrorMapper.CloudError(
                                            headline = "Endpoint and API key are required.",
                                            suggestion = "Paste both, then tap Test connection.",
                                            technical = "",
                                        )
                                    )
                                } else {
                                    val effectiveKey = aiKey.ifBlank { savedAiKey }
                                    val effectiveModel = aiModel.trim().ifBlank { null }
                                    connectionStatus = null
                                    modelRecommendationInProgress = true
                                    scope.launch {
                                        val status = OpenAiCompatibleAiProvider(
                                            AiProviderConfig(
                                                mode = AiMode.CLOUD,
                                                providerId = "openai_compatible",
                                                modelId = effectiveModel,
                                                endpoint = openAiEndpoint.trim(),
                                            ),
                                            effectiveKey,
                                        ).testConnection().getOrElse { ConnectionStatus.Failed(
                                            com.talom.core.ai.CloudErrorMapper.mapException(it, openAiEndpoint.trim())
                                        ) }
                                        connectionStatus = status
                                        if (status is ConnectionStatus.Ok && effectiveModel == null && availableModels.isNotEmpty()) {
                                            val recommended = ModelRecommender.recommend(
                                                endpoint = openAiEndpoint.trim(),
                                                apiKey = effectiveKey,
                                                candidates = availableModels,
                                                candidateModel = availableModels.first(),
                                            )
                                            recommended?.let {
                                                aiModel = it
                                                aiPreferences.setConfig(
                                                    AiProviderConfig(
                                                        mode = aiMode,
                                                        providerId = "openai_compatible",
                                                        modelId = it,
                                                        endpoint = openAiEndpoint.trim(),
                                                    )
                                                )
                                            }
                                        }
                                        modelRecommendationInProgress = false
                                    }
                                }
                            }
                            val doRefreshModels: () -> Unit = {
                                // Bump a counter so the LaunchedEffect re-runs and re-fetches
                                // the model list (regardless of whether endpoint/key changed).
                                modelsRefreshKey++
                            }
                            SettingsNavGraph(
                                aiMode = aiMode,
                                onAiModeChange = { aiMode = it },
                                aiKey = aiKey,
                                onAiKeyChange = { aiKey = it },
                                aiModel = aiModel,
                                onAiModelChange = { aiModel = it },
                                ollamaEndpoint = ollamaEndpoint,
                                onOllamaEndpointChange = { ollamaEndpoint = it },
                                openAiEndpoint = openAiEndpoint,
                                onOpenAiEndpointChange = { openAiEndpoint = it },
                                useOpenAiCompatible = useOpenAiCompatible,
                                onUseOpenAiCompatibleChange = {
                                    useOpenAiCompatible = it
                                    if (it) {
                                        if (openAiEndpoint.isBlank() ||
                                            openAiEndpoint.contains("localhost") ||
                                            openAiEndpoint.contains("127.0.0.1")
                                        ) {
                                            openAiEndpoint = "https://openrouter.ai/api/v1"
                                        }
                                        // Ollama model id won't work on OpenRouter — clear for re-pick.
                                        if (aiModel.contains(":") && !aiModel.contains("/")) {
                                            aiModel = ""
                                        }
                                    }
                                },
                                cloudConsent = cloudConsent,
                                onCloudConsentChange = { cloudConsent = it },
                                localConsent = localConsent,
                                onLocalConsentChange = { localConsent = it },
                                aiSettingsState = aiSettingsState,
                                localTestState = localTestState,
                                availableModels = availableModels,
                                modelsLoading = modelsLoading,
                                modelsError = modelsError,
                                hasSavedKey = savedAiKey.isNotBlank(),
                                availableLocalModels = availableLocalModels,
                                localModelsLoading = localModelsLoading,
                                localModelsError = localModelsError,
                                messageWindowDays = messageWindowDays,
                                onMessageWindowChange = {
                                    messageWindowDays = it
                                    aiPreferences.setMessageWindowDays(it)
                                },
                                onSaveAi = doSaveAi,
                                onTestOllama = doTestOllama,
                                onTestConnection = doTestConnection,
                                onRefreshModels = doRefreshModels,
                                connectionStatus = connectionStatus,
                                modelRecommendationInProgress = modelRecommendationInProgress,
                                whitelist = whitelist,
                                pulling = pulling,
                                pullState = pullState,
                                importState = importState,
                                onPull = {
                                    pulling = true
                                    pullState = "Creating local WhatsApp snapshot..."
                                    scope.launch {
                                        val result = pullRepository.pull(force = false)
                                        pulling = false
                                        val providerLabel = aiPreferences.config().let { cfg ->
                                            when (cfg.mode) {
                                                AiMode.DISABLED -> null
                                                AiMode.LOCAL -> "${cfg.providerId ?: "ollama"}/${cfg.modelId ?: "?"}"
                                                AiMode.CLOUD -> "${cfg.providerId ?: "cloud"}/${cfg.modelId ?: "?"}"
                                            }
                                        }
                                        pullState = result.fold(
                                            onSuccess = {
                                                com.talom.ui.settings.StatusText.pullResult(
                                                    extracted = it.extractedCount,
                                                    acad = it.academicItemCount,
                                                    ins = it.insightCount,
                                                    windowDays = aiPreferences.messageWindowDays(),
                                                    force = false,
                                                    providerLabel = providerLabel,
                                                    noNewMessagesCursorMillis = it.nextCursor?.timestampMillis,
                                                )
                                            },
                                            onFailure = {
                                                com.talom.ui.settings.StatusText.pullFailure(
                                                    it.message ?: "Unknown error", force = false,
                                                )
                                            },
                                        )
                                    }
                                },
                                onForcePull = {
                                    pulling = true
                                    pullState = "Re-extracting last ${aiPreferences.messageWindowDays()}d with AI..."
                                    scope.launch {
                                        val result = pullRepository.pull(force = true)
                                        pulling = false
                                        val providerLabel = aiPreferences.config().let { cfg ->
                                            when (cfg.mode) {
                                                AiMode.DISABLED -> null
                                                AiMode.LOCAL -> "${cfg.providerId ?: "ollama"}/${cfg.modelId ?: "?"}"
                                                AiMode.CLOUD -> "${cfg.providerId ?: "cloud"}/${cfg.modelId ?: "?"}"
                                            }
                                        }
                                        pullState = result.fold(
                                            onSuccess = {
                                                com.talom.ui.settings.StatusText.pullResult(
                                                    extracted = it.extractedCount,
                                                    acad = it.academicItemCount,
                                                    ins = it.insightCount,
                                                    windowDays = aiPreferences.messageWindowDays(),
                                                    force = true,
                                                    providerLabel = providerLabel,
                                                )
                                            },
                                            onFailure = {
                                                com.talom.ui.settings.StatusText.pullFailure(
                                                    it.message ?: "Unknown error", force = true,
                                                )
                                            },
                                        )
                                    }
                                },
                                onImport = { importLauncher.launch(arrayOf("text/plain", "text/*")) },
                                pullLog = pullLog,
                                formatTime = ::formatTime,
                                directory = directory,
                                directoryQuery = directoryQuery,
                                onDirectoryQueryChange = { directoryQuery = it },
                                directoryState = directoryState,
                                directoryLoading = directoryLoading,
                                onLoadDirectory = {
                                    loadDirectory()
                                },
                                onWhitelistWithCategory = { conv, cat ->
                                    scope.launch {
                                        whitelistDao.insert(
                                            WhatsAppWhitelist(
                                                jid = conv.jid,
                                                label = conv.label,
                                                addedAtMillis = System.currentTimeMillis(),
                                                category = cat,
                                            ),
                                        )
                                    }
                                },
                                onUpdateCategory = { item, cat ->
                                    scope.launch { whitelistDao.insert(item.copy(category = cat)) }
                                },
                                onRemove = { item -> scope.launch { whitelistDao.delete(item.jid) } },
                                classroomAccount = classroomAccount,
                                classroomStatus = classroomStatus,
                                classroomSyncing = classroomSyncing,
                                onConnectClassroom = {
                                    val intent = AccountManager.newChooseAccountIntent(
                                        null, null, arrayOf("com.google"), null, null, null, null,
                                    )
                                    accountPickerLauncher.launch(intent)
                                },
                                onSyncClassroom = {
                                    val email = classroomAccount ?: return@SettingsNavGraph
                                    classroomSyncing = true
                                    scope.launch {
                                        ClassroomRepository(
                                            provider = GoogleClassroomReadOnlyProvider(GoogleAccountTokenProvider(context, email)),
                                            dao = database.classroomDao(),
                                            statusDao = database.sourceStatusDao(),
                                        ).refresh()
                                        classroomSyncing = false
                                    }
                                },
                                themeMode = themeMode,
                                onThemeChange = {
                                    themeMode = it
                                    context.getSharedPreferences("talom_preferences", android.content.Context.MODE_PRIVATE)
                                        .edit().putString("theme_mode", it.name).apply()
                                },
                                fontPreference = fontPreference,
                                onFontPreferenceChange = {
                                    fontPreference = it
                                    talomPreferences.setFontPreference(it)
                                },
                                pullHour = pullHour,
                                pullMinute = pullMinute,
                                onPullTimeChange = { hour, minute ->
                                    pullHour = hour
                                    pullMinute = minute
                                    talomPreferences.setPullTime(hour, minute)
                                    TalomWorkScheduler.scheduleDailyAt(context, hour, minute)
                                },
                                userIdentity = userIdentity,
                                onUserIdentityChange = {
                                    userIdentity = it
                                    talomPreferences.setUserIdentity(it)
                                },
                                showNotificationPrompt = showNotificationPrompt,
                                onEnableNotifications = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                tabReselected = settingsTabReselected,
                                onTabReselectHandled = { settingsTabReselected = false },
                            )
    }
    }
    }
    }
    }
    }
    }
    }
}

private fun String.isValidWhatsAppJid(): Boolean =
    matches(Regex("""[0-9]+@(g\.us|s\.whatsapp\.net)"""))

private fun stableExportMessageId(stableId: String): Long =
    stableId.take(16).toULong(16).toLong()

private fun formatTime(millis: Long): String =
    DateTimeFormatter.ofPattern("MMM d, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
