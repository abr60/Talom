package com.talom.ui.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.talom.core.ai.AiMode
import com.talom.core.ai.ConnectionStatus
import com.talom.core.ai.OpenAiCompatibleAiProvider
import com.talom.data.source.PullLogEntity
import com.talom.data.source.SourceStatusEntity
import com.talom.data.whatsapp.RelationCategory
import com.talom.data.whatsapp.WhatsAppWhitelist
import com.talom.source.whatsapp.WhatsAppConversation
import com.talom.ui.theme.TalomThemeMode
import kotlinx.coroutines.delay

private object SettingsRoutes {
    const val HUB = "settings/hub"
    const val AI = "settings/ai"
    const val WHATSAPP = "settings/whatsapp"
    const val MANAGE = "settings/manage"
    const val CLASSROOM = "settings/classroom"
}

@Composable
fun SettingsNavGraph(
    // AI / Gemini / Ollama
    aiMode: AiMode,
    onAiModeChange: (AiMode) -> Unit,
    aiKey: String,
    onAiKeyChange: (String) -> Unit,
    aiModel: String,
    onAiModelChange: (String) -> Unit,
    aiEndpoint: String,
    onAiEndpointChange: (String) -> Unit,
    useOpenAiCompatible: Boolean,
    onUseOpenAiCompatibleChange: (Boolean) -> Unit,
    cloudConsent: Boolean,
    onCloudConsentChange: (Boolean) -> Unit,
    localConsent: Boolean,
    onLocalConsentChange: (Boolean) -> Unit,
    aiSettingsState: String,
    localTestState: String,
    availableModels: List<String>,
    modelsLoading: Boolean,
    modelsError: String?,
    hasSavedKey: Boolean,
    availableLocalModels: List<String>,
    localModelsLoading: Boolean,
    localModelsError: String?,
    messageWindowDays: Int,
    onMessageWindowChange: (Int) -> Unit,
    onSaveAi: () -> Unit,
    onTestOllama: () -> Unit,
    onTestConnection: () -> Unit,
    onRefreshModels: () -> Unit,
    connectionStatus: ConnectionStatus?,
    modelRecommendationInProgress: Boolean,

    // WhatsApp
    whitelist: List<WhatsAppWhitelist>,
    pulling: Boolean,
    pullState: String,
    importState: String,
    onPull: () -> Unit,
    onImport: () -> Unit,
    pullLog: List<PullLogEntity>,
    formatTime: (Long) -> String,
    directory: List<WhatsAppConversation>,
    directoryQuery: String,
    onDirectoryQueryChange: (String) -> Unit,
    directoryState: String,
    directoryLoading: Boolean,
    onLoadDirectory: () -> Unit,
    onWhitelistWithCategory: (WhatsAppConversation, RelationCategory) -> Unit,
    onUpdateCategory: (WhatsAppWhitelist, RelationCategory) -> Unit,
    onRemove: (WhatsAppWhitelist) -> Unit,

    // Classroom
    classroomAccount: String?,
    classroomStatus: SourceStatusEntity?,
    classroomSyncing: Boolean,
    onConnectClassroom: () -> Unit,
    onSyncClassroom: () -> Unit,

    // Theme + notifications
    themeMode: TalomThemeMode,
    onThemeChange: (TalomThemeMode) -> Unit,
    showNotificationPrompt: Boolean,
    onEnableNotifications: () -> Unit,

    // Tab reselect (MainActivity sets true when user re-taps the Settings tab)
    tabReselected: Boolean,
    onTabReselectHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var showExitHint by remember { mutableStateOf(false) }

    // When the user re-taps the Settings tab while it's already selected, pop to hub.
    LaunchedEffect(tabReselected) {
        if (tabReselected) {
            navController.popBackStack(SettingsRoutes.HUB, inclusive = false)
            onTabReselectHandled()
        }
    }

    // Hub back: first press shows "Swipe again to exit", second press within
    // 2s exits the app. Only fires when there's nothing to pop in the graph.
    BackHandler(enabled = navController.previousBackStackEntry == null) {
        if (showExitHint) {
            (context as? Activity)?.finish()
        } else {
            showExitHint = true
        }
    }
    LaunchedEffect(showExitHint) {
        if (showExitHint) {
            delay(2000)
            showExitHint = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = SettingsRoutes.HUB,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(SettingsRoutes.HUB) {
                SettingsHubScreen(
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    aiMode = aiMode,
                    whitelist = whitelist,
                    classroomAccount = classroomAccount,
                    showNotificationPrompt = showNotificationPrompt,
                    onEnableNotifications = onEnableNotifications,
                    onNavigateAi = { navController.navigate(SettingsRoutes.AI) },
                    onNavigateWhatsApp = { navController.navigate(SettingsRoutes.WHATSAPP) },
                    onNavigateClassroom = { navController.navigate(SettingsRoutes.CLASSROOM) },
                )
            }
            composable(SettingsRoutes.AI) {
                AiSettingsScreen(
                    aiMode = aiMode,
                    onAiModeChange = onAiModeChange,
                    aiKey = aiKey,
                    onAiKeyChange = onAiKeyChange,
                    aiModel = aiModel,
                    onAiModelChange = onAiModelChange,
                    aiEndpoint = aiEndpoint,
                    onAiEndpointChange = onAiEndpointChange,
                    useOpenAiCompatible = useOpenAiCompatible,
                    onUseOpenAiCompatibleChange = onUseOpenAiCompatibleChange,
                    cloudConsent = cloudConsent,
                    onCloudConsentChange = onCloudConsentChange,
                    localConsent = localConsent,
                    onLocalConsentChange = onLocalConsentChange,
                    aiSettingsState = aiSettingsState,
                    localTestState = localTestState,
                    availableModels = availableModels,
                    modelsLoading = modelsLoading,
                    modelsError = modelsError,
                    hasSavedKey = hasSavedKey,
                    availableLocalModels = availableLocalModels,
                    localModelsLoading = localModelsLoading,
                    localModelsError = localModelsError,
                    messageWindowDays = messageWindowDays,
                    onMessageWindowChange = onMessageWindowChange,
                    onSaveAi = onSaveAi,
                    onTestOllama = onTestOllama,
                    onTestConnection = onTestConnection,
                    onRefreshModels = onRefreshModels,
                    connectionStatus = connectionStatus,
                    modelRecommendationInProgress = modelRecommendationInProgress,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(SettingsRoutes.WHATSAPP) {
                WhatsAppSettingsScreen(
                    whitelist = whitelist,
                    pulling = pulling,
                    pullState = pullState,
                    importState = importState,
                    onPull = onPull,
                    onImport = onImport,
                    pullLog = pullLog,
                    formatTime = formatTime,
                    onManageConversations = { navController.navigate(SettingsRoutes.MANAGE) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(SettingsRoutes.MANAGE) {
                ManageConversationsScreen(
                    whitelist = whitelist,
                    directory = directory,
                    directoryQuery = directoryQuery,
                    onDirectoryQueryChange = onDirectoryQueryChange,
                    directoryState = directoryState,
                    directoryLoading = directoryLoading,
                    onLoadDirectory = onLoadDirectory,
                    onUpdateCategory = onUpdateCategory,
                    onRemove = onRemove,
                    onWhitelistWithCategory = onWhitelistWithCategory,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(SettingsRoutes.CLASSROOM) {
                ClassroomSettingsScreen(
                    classroomAccount = classroomAccount,
                    classroomStatus = classroomStatus,
                    classroomSyncing = classroomSyncing,
                    onConnectClassroom = onConnectClassroom,
                    onSyncClassroom = onSyncClassroom,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        if (showExitHint) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Swipe again to exit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
