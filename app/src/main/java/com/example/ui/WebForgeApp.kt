package com.example.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.FileManagerSheet
import com.example.ui.components.GeminiAiStudioSheet
import com.example.ui.components.QrCodeDialog
import com.example.ui.components.TemplatesBrowserSheet
import com.example.ui.components.ToolsHubSheet
import com.example.ui.components.UrlGithubImportDialog
import com.example.ui.components.WebForgeTopBar
import com.example.ui.i18n.LocalStrings
import com.example.ui.i18n.stringsFor
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PreviewScreen
import com.example.ui.screens.ProjectsSettingsScreen
import com.example.ui.theme.LocalWebForgeColors
import com.example.ui.theme.WebForgePrimary
import com.example.ui.theme.WebForgeTheme

const val ROUTE_HOME = "home"
const val ROUTE_EDITOR = "editor"
const val ROUTE_PREVIEW = "preview"
const val ROUTE_EXPORT = "export"
const val ROUTE_PROJECTS = "projects"

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

@Composable
fun WebForgeApp(viewModel: WebForgeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedProjects by viewModel.savedProjects.collectAsStateWithLifecycle()
    val historySnapshots by viewModel.activeHistory.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val strings = stringsFor(state.settings.language)
    val layoutDirection = if (state.settings.language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingSafSave by remember { mutableStateOf<UiEvent.SaveBytesToSaf?>(null) }
    var pendingWebPQuality by remember { mutableIntStateOf(80) }

    // Global Modal Sheet / Dialog States
    var showTemplatesSheet by remember { mutableStateOf(false) }
    var showFileManagerSheet by remember { mutableStateOf(false) }
    var showUrlGithubDialog by remember { mutableStateOf(false) }
    var showAiStudioSheet by remember { mutableStateOf(false) }
    var aiInitialPrompt by remember { mutableStateOf<String?>(null) }
    var showToolsHubSheet by remember { mutableStateOf(false) }
    var showPreviewQrDialog by remember { mutableStateOf(false) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) viewModel.importFileFromUri(uri) },
    )

    val attachAssetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) viewModel.addAssetFromUri(uri) },
    )

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) viewModel.importBackupFromUri(uri) },
    )

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
        onResult = { uri ->
            val payload = pendingSafSave
            pendingSafSave = null
            if (uri != null && payload != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.bytes)
                    }
                }.onSuccess {
                    viewModel.emitMessage(strings.exportedSuccess)
                }.onFailure { err ->
                    viewModel.emitMessage(err.localizedMessage ?: "Export failed")
                }
            }
        },
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) viewModel.setCustomIconFromUri(uri) },
    )

    val webpPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) viewModel.compressImageUriToWebP(uri, pendingWebPQuality) },
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
                is UiEvent.SaveBytesToSaf -> {
                    pendingSafSave = event
                    createDocumentLauncher.launch(event.suggestedName)
                }
                is UiEvent.ShareFile -> {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        event.file,
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, event.chooserTitle))
                }
            }
        }
    }

    WebForgeTheme(themeMode = state.settings.themeMode) {
        CompositionLocalProvider(
            LocalLayoutDirection provides layoutDirection,
            LocalStrings provides strings,
        ) {
            if (!state.settings.hasCompletedOnboarding) {
                OnboardingScreen(
                    isRtl = state.settings.language.isRtl,
                    onComplete = viewModel::completeOnboarding,
                )
                return@CompositionLocalProvider
            }

            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME

            val tabs = listOf(
                NavTab(ROUTE_HOME, strings.navHome, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
                NavTab(ROUTE_EDITOR, strings.navEditor, Icons.Rounded.Code, Icons.Outlined.Code, "nav_editor"),
                NavTab(ROUTE_PREVIEW, strings.navPreview, Icons.Rounded.PlayCircle, Icons.Outlined.PlayCircle, "nav_preview"),
                NavTab(ROUTE_EXPORT, strings.navExport, Icons.Rounded.RocketLaunch, Icons.Outlined.RocketLaunch, "nav_export"),
                NavTab(ROUTE_PROJECTS, strings.navProjects, Icons.Rounded.FolderCopy, Icons.Outlined.FolderCopy, "nav_projects"),
            )

            fun navigateToTab(route: String) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            val ext = LocalWebForgeColors.current
            val ambientBackground = if (ext.isDark) {
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        Color(0xFF120F2E),
                        MaterialTheme.colorScheme.background,
                    ),
                )
            } else {
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background))
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ambientBackground),
            ) {
                val useRail = maxWidth >= 680.dp

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                tonalElevation = 0.dp,
                                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                            ) {
                                tabs.forEach { tab ->
                                    val selected = currentRoute == tab.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateToTab(tab.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label,
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = tab.label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = ext.accent,
                                            selectedTextColor = ext.accent,
                                            indicatorColor = WebForgePrimary.copy(alpha = 0.25f),
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        modifier = Modifier.testTag(tab.testTag),
                                    )
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        if (useRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                modifier = Modifier.fillMaxHeight(),
                            ) {
                                tabs.forEach { tab ->
                                    val selected = currentRoute == tab.route
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateToTab(tab.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label,
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = ext.accent,
                                            selectedTextColor = ext.accent,
                                            indicatorColor = WebForgePrimary.copy(alpha = 0.25f),
                                        ),
                                        modifier = Modifier.testTag(tab.testTag),
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            WebForgeTopBar(
                                projectName = state.projectName,
                                isOffline = state.isEffectiveOffline,
                                onLogoClick = { navigateToTab(ROUTE_HOME) },
                            )

                            NavHost(
                                navController = navController,
                                startDestination = ROUTE_HOME,
                                enterTransition = { fadeIn() },
                                exitTransition = { fadeOut() },
                                modifier = Modifier.weight(1f),
                            ) {
                                composable(ROUTE_HOME) {
                                    HomeScreen(
                                        state = state,
                                        savedProjects = savedProjects,
                                        onNewBlankProject = {
                                            viewModel.createBlankNewProject()
                                            navigateToTab(ROUTE_EDITOR)
                                        },
                                        onContinueEditor = { navigateToTab(ROUTE_EDITOR) },
                                        onOpenProjects = { navigateToTab(ROUTE_PROJECTS) },
                                        onOpenTemplates = { showTemplatesSheet = true },
                                        onOpenToolsHub = { showToolsHubSheet = true },
                                        onOpenAiStudio = { prompt ->
                                            aiInitialPrompt = prompt
                                            showAiStudioSheet = true
                                        },
                                        onOpenImportDialog = { showUrlGithubDialog = true },
                                        onReopenProject = { id ->
                                            viewModel.reopenProject(id)
                                            navigateToTab(ROUTE_EDITOR)
                                        },
                                    )
                                }

                                composable(ROUTE_EDITOR) {
                                    EditorScreen(
                                        state = state,
                                        onProjectNameChange = viewModel::updateProjectName,
                                        onCodeChange = { code -> viewModel.updateCode(code, recordUndo = true) },
                                        onUndo = viewModel::undoCode,
                                        onRedo = viewModel::redoCode,
                                        onSelectAssetTab = viewModel::selectAssetTab,
                                        onOpenFileManager = { showFileManagerSheet = true },
                                        onOpenTemplates = { showTemplatesSheet = true },
                                        onOpenUrlGithubImport = { showUrlGithubDialog = true },
                                        onOpenAiStudio = {
                                            aiInitialPrompt = null
                                            showAiStudioSheet = true
                                        },
                                        onPasteHtml = viewModel::pasteHtml,
                                        onUploadClick = {
                                            openDocumentLauncher.launch(
                                                arrayOf("text/html", "application/zip", "application/x-zip-compressed", "*/*"),
                                            )
                                        },
                                        onConvertClick = {
                                            viewModel.reloadPreview()
                                            navigateToTab(ROUTE_PREVIEW)
                                        },
                                        onFormatClick = viewModel::formatCurrentCode,
                                        onMinifyClick = viewModel::minifyProjectCode,
                                        onFixLinksClick = viewModel::fixRelativeLinks,
                                        onAutoSeoClick = viewModel::injectSeoViewportAndIcon,
                                        onApplyLintFix = viewModel::applyLintFix,
                                        onExpandEmmet = viewModel::expandEmmet,
                                        onLoadStarter = viewModel::createBlankNewProject,
                                        onClearCode = viewModel::clearCode,
                                        onSaveProject = { viewModel.saveCurrentProject(withSnapshot = true) },
                                        onConsoleLog = viewModel::appendConsoleLog,
                                        onCopiedFeedback = { viewModel.emitMessage(strings.copiedSuccess) },
                                    )
                                }

                                composable(ROUTE_PREVIEW) {
                                    PreviewScreen(
                                        state = state,
                                        onSelectDevice = viewModel::selectDeviceFrame,
                                        onToggleOrientation = viewModel::togglePreviewOrientation,
                                        onReload = viewModel::reloadPreview,
                                        onConsoleLog = viewModel::appendConsoleLog,
                                        onShowQr = { showPreviewQrDialog = true },
                                        onPublishClick = { navigateToTab(ROUTE_EXPORT) },
                                    )
                                }

                                composable(ROUTE_EXPORT) {
                                    ExportScreen(
                                        state = state,
                                        onExportWebsiteZip = viewModel::exportWebsiteZip,
                                        onExportSingleHtml = viewModel::exportSingleSelfContainedHtml,
                                        onExportAndroidZip = viewModel::exportAndroidWebViewProject,
                                        onExportKeystoreGuide = viewModel::exportKeystoreGuideZip,
                                        onExportIosZip = viewModel::exportIosCapacitorZip,
                                        onExportElectronZip = viewModel::exportElectronDesktopZip,
                                        onExportStorePack = viewModel::exportStorePackZip,
                                        onExportQrPng = viewModel::exportQrPng,
                                        onInjectPwa = viewModel::injectPwaSupport,
                                        onDeploy = viewModel::deployWebsite,
                                        onResetDeployState = viewModel::resetDeployState,
                                        onUpdateAppConfig = viewModel::updateAppConfig,
                                        onPickCustomIcon = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                            )
                                        },
                                        onResetDefaultIcon = viewModel::resetDefaultIcon,
                                        onOpenUrl = { url ->
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                                        },
                                        onCopiedFeedback = { viewModel.emitMessage(strings.copiedSuccess) },
                                    )
                                }

                                composable(ROUTE_PROJECTS) {
                                    ProjectsSettingsScreen(
                                        state = state,
                                        savedProjects = savedProjects,
                                        historySnapshots = historySnapshots,
                                        onSaveProjectNow = { viewModel.saveCurrentProject(withSnapshot = false) },
                                        onSaveSnapshot = { viewModel.saveCurrentProject(withSnapshot = true) },
                                        onReopenProject = { id ->
                                            viewModel.reopenProject(id)
                                            navigateToTab(ROUTE_EDITOR)
                                        },
                                        onToggleFavorite = viewModel::toggleFavoriteProject,
                                        onDuplicateProject = viewModel::duplicateProject,
                                        onDeleteProject = viewModel::deleteProject,
                                        onRestoreSnapshot = { snapId ->
                                            viewModel.restoreSnapshot(snapId)
                                            navigateToTab(ROUTE_EDITOR)
                                        },
                                        onExportBackup = viewModel::exportFullBackup,
                                        onImportBackup = {
                                            importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                                        },
                                        onLanguageChange = viewModel::setLanguage,
                                        onThemeModeChange = viewModel::setThemeMode,
                                        onEditorFontSizeChange = viewModel::setEditorFontSize,
                                        onEditorThemeChange = viewModel::setEditorTheme,
                                        onAutoSaveChange = viewModel::setAutoSave,
                                        onOfflineModeChange = viewModel::setOfflineMode,
                                        onClearTokens = viewModel::clearSavedTokens,
                                        onClearAllData = viewModel::clearAllData,
                                        onReopenOnboarding = viewModel::reopenOnboarding,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showTemplatesSheet) {
                TemplatesBrowserSheet(
                    isRtl = state.settings.language.isRtl,
                    onSelectTemplate = { tpl ->
                        viewModel.applyTemplate(tpl)
                        navigateToTab(ROUTE_EDITOR)
                    },
                    onDismiss = { showTemplatesSheet = false },
                )
            }

            if (showFileManagerSheet) {
                FileManagerSheet(
                    assets = state.assets,
                    selectedPath = state.selectedAssetPath,
                    onSelectFile = viewModel::selectAssetTab,
                    onCreateFile = viewModel::createProjectFile,
                    onRenameFile = viewModel::renameOrMoveFile,
                    onDeleteFile = viewModel::deleteProjectFile,
                    onAttachLocalFile = { attachAssetLauncher.launch(arrayOf("*/*")) },
                    onDismiss = { showFileManagerSheet = false },
                )
            }

            if (showUrlGithubDialog) {
                UrlGithubImportDialog(
                    onImportUrl = { url ->
                        viewModel.importFromUrl(url)
                        navigateToTab(ROUTE_EDITOR)
                    },
                    onImportGitHub = { owner, repo, branch, token ->
                        viewModel.importFromGitHub(owner, repo, branch, token)
                        navigateToTab(ROUTE_EDITOR)
                    },
                    onDismiss = { showUrlGithubDialog = false },
                )
            }

            if (showAiStudioSheet) {
                GeminiAiStudioSheet(
                    initialPrompt = aiInitialPrompt,
                    currentCode = state.currentEditorCode,
                    aiTaskState = state.aiTaskState,
                    chatHistory = state.aiChatHistory,
                    isOffline = state.isEffectiveOffline,
                    isRtl = state.settings.language.isRtl,
                    onGenerateSite = viewModel::generateSiteFromPrompt,
                    onQuickAction = viewModel::runAiQuickAction,
                    onSendChat = viewModel::sendAiChatMessage,
                    onApplyProposedCode = { code ->
                        viewModel.applyAiProposedCode(code)
                        showAiStudioSheet = false
                        navigateToTab(ROUTE_EDITOR)
                    },
                    onDiscardTask = viewModel::discardAiTask,
                    onDismiss = { showAiStudioSheet = false },
                )
            }

            if (showToolsHubSheet) {
                ToolsHubSheet(
                    isRtl = state.settings.language.isRtl,
                    auditReport = state.auditReport,
                    lastWebPResult = state.lastWebPResult,
                    onRunAudit = viewModel::runSiteAudit,
                    onPickImageForWebP = { quality ->
                        pendingWebPQuality = quality
                        webpPhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onAddWebPToAssets = viewModel::addLastWebPToProjectAssets,
                    onExportIconSplashPack = viewModel::exportIconSplashPackZip,
                    onExportSingleHtml = viewModel::exportSingleSelfContainedHtml,
                    onInjectTrackingAndCookies = viewModel::injectTrackingAndCookies,
                    onDismiss = { showToolsHubSheet = false },
                )
            }

            if (showPreviewQrDialog) {
                QrCodeDialog(
                    initialUrl = state.previewDisplayUrl,
                    onCopyUrl = { url ->
                        clipboard.setText(AnnotatedString(url))
                        viewModel.emitMessage(strings.copiedSuccess)
                    },
                    onExportQr = viewModel::exportQrPng,
                    onDismiss = { showPreviewQrDialog = false },
                )
            }
        }
    }
}
