package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiQuickAction
import com.example.ai.GeminiStudioAssistant
import com.example.data.local.SettingsRepository
import com.example.data.local.UserSettings
import com.example.data.local.WebForgeDatabase
import com.example.data.model.AiChatMessage
import com.example.data.model.AiTaskState
import com.example.data.model.AppExportConfig
import com.example.data.model.AppLanguage
import com.example.data.model.AuditReport
import com.example.data.model.CodeIssue
import com.example.data.model.ConsoleEntry
import com.example.data.model.DeployState
import com.example.data.model.DeviceFrame
import com.example.data.model.EditorTheme
import com.example.data.model.HostingProvider
import com.example.data.model.ProjectAsset
import com.example.data.model.ProjectSnapshot
import com.example.data.model.ProjectSummary
import com.example.data.model.TemplateItem
import com.example.data.model.ThemeMode
import com.example.data.repository.ProjectRepository
import com.example.domain.AssetAndStoreGenerator
import com.example.domain.CodeLinterAndAudit
import com.example.domain.HostingDeployer
import com.example.domain.HtmlToolkit
import com.example.domain.QrCodeGenerator
import com.example.domain.WebImporter
import com.example.domain.WebPConversionResult
import com.example.domain.ZipArchiveManager
import com.example.domain.apk.OnDeviceApkBuilder
import com.example.ui.i18n.stringsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class WebForgeUiState(
    val activeProjectId: Long = 0L,
    val projectName: String = "موقعي الجديد",
    val htmlContent: String = HtmlToolkit.defaultStarterHtml(isArabic = true),
    val assets: List<ProjectAsset> = emptyList(),
    val selectedAssetPath: String? = null,
    val appConfig: AppExportConfig = AppExportConfig(appName = "موقعي الجديد"),
    val isFavorite: Boolean = false,
    val selectedDevice: DeviceFrame = DeviceFrame.PHONE,
    val isLandscapePreview: Boolean = false,
    val publishedUrl: String? = null,
    val lastProvider: HostingProvider? = null,
    val deployState: DeployState = DeployState.Idle,
    val consoleLogs: List<ConsoleEntry> = emptyList(),
    val previewRevision: Int = 0,
    val settings: UserSettings = UserSettings(),
    val isDeviceOnline: Boolean = true,
    val isImporting: Boolean = false,
    val codeIssues: List<CodeIssue> = emptyList(),
    val auditReport: AuditReport? = null,
    val aiTaskState: AiTaskState = AiTaskState.Idle,
    val aiChatHistory: List<AiChatMessage> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val lastWebPResult: WebPConversionResult? = null,
    val isGeneratingStorePack: Boolean = false,
    val isBuildingApk: Boolean = false,
) {
    val isEffectiveOffline: Boolean
        get() = settings.offlineMode || !isDeviceOnline

    val currentEditorCode: String
        get() = if (selectedAssetPath == null) {
            htmlContent
        } else {
            assets.firstOrNull { it.path == selectedAssetPath }?.textContent ?: htmlContent
        }

    val previewDisplayUrl: String
        get() = publishedUrl ?: if (appConfig.customDomain.isNotBlank()) {
            "https://${appConfig.customDomain.trim()}"
        } else {
            "https://${HtmlToolkit.slugify(projectName)}.web.app"
        }
}

sealed interface UiEvent {
    data class ShowMessage(val text: String) : UiEvent
    data class ShareFile(val file: File, val mimeType: String, val chooserTitle: String) : UiEvent
    data class SaveBytesToSaf(val suggestedName: String, val mimeType: String, val bytes: ByteArray) : UiEvent
}

class WebForgeViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val settingsRepo = SettingsRepository(context)
    private val database = WebForgeDatabase.getInstance(context)
    private val projectRepo = ProjectRepository(database.projectDao())
    private val deployer = HostingDeployer()
    private val geminiAssistant = GeminiStudioAssistant(context)

    private val _uiState = MutableStateFlow(WebForgeUiState())
    val uiState: StateFlow<WebForgeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val savedProjects: StateFlow<List<ProjectSummary>> = projectRepo.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeHistory: StateFlow<List<ProjectSnapshot>> = _uiState
        .flatMapLatest { state ->
            if (state.activeProjectId > 0L) projectRepo.observeHistory(state.activeProjectId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var autoSaveJob: Job? = null
    private var lintJob: Job? = null
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    init {
        observeConnectivity()
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            val initialSettings = settingsRepo.settingsFlow.first()
            if (initialSettings.lastProjectId > 0L) {
                val loaded = withContext(Dispatchers.IO) {
                    projectRepo.loadProject(initialSettings.lastProjectId)
                }
                if (loaded != null) {
                    _uiState.update {
                        it.copy(
                            activeProjectId = loaded.id,
                            projectName = loaded.name,
                            htmlContent = loaded.htmlContent,
                            assets = loaded.assets,
                            appConfig = loaded.appConfig,
                            publishedUrl = loaded.publishedUrl,
                            lastProvider = loaded.lastProvider,
                            isFavorite = loaded.isFavorite,
                            previewRevision = it.previewRevision + 1,
                        )
                    }
                    scheduleLintDetection(loaded.htmlContent)
                    return@launch
                }
            }
            val starterHtml = HtmlToolkit.defaultStarterHtml(initialSettings.language.isRtl)
            val starterName = if (initialSettings.language.isRtl) "موقعي التفاعلي" else "My Interactive Site"
            _uiState.update {
                it.copy(
                    projectName = starterName,
                    htmlContent = starterHtml,
                    appConfig = it.appConfig.copy(appName = starterName),
                )
            }
            scheduleLintDetection(starterHtml)
        }
    }

    private fun observeConnectivity() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val activeCap = cm.getNetworkCapabilities(cm.activeNetwork)
        val initialOnline = activeCap?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _uiState.update { it.copy(isDeviceOnline = initialOnline) }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _uiState.update { it.copy(isDeviceOnline = true) }
            }

            override fun onLost(network: Network) {
                val cap = cm.getNetworkCapabilities(cm.activeNetwork)
                val stillOnline = cap?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                _uiState.update { it.copy(isDeviceOnline = stillOnline) }
            }
        }
        connectivityCallback = callback
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }

    override fun onCleared() {
        super.onCleared()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityCallback?.let { runCatching { cm?.unregisterNetworkCallback(it) } }
    }

    private fun strings() = stringsFor(_uiState.value.settings.language)

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepo.setOnboardingDone(true) }
    }

    fun reopenOnboarding() {
        viewModelScope.launch { settingsRepo.setOnboardingDone(false) }
    }

    private fun scheduleLintDetection(code: String) {
        lintJob?.cancel()
        lintJob = viewModelScope.launch {
            delay(380)
            val issues = withContext(Dispatchers.Default) {
                CodeLinterAndAudit.detectCodeIssues(code)
            }
            _uiState.update { it.copy(codeIssues = issues) }
        }
    }

    fun updateCode(newCode: String, recordUndo: Boolean = true) {
        val state = _uiState.value
        val previous = state.currentEditorCode
        if (recordUndo && previous != newCode) {
            val maxUndo = if (newCode.length > 80_000) 5 else 25
            while (undoStack.size >= maxUndo) undoStack.removeFirst()
            undoStack.addLast(previous)
            redoStack.clear()
        }

        val selected = state.selectedAssetPath
        if (selected == null) {
            _uiState.update {
                it.copy(
                    htmlContent = newCode,
                    previewRevision = it.previewRevision + 1,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                )
            }
            scheduleLintDetection(newCode)
        } else {
            _uiState.update { st ->
                val updatedAssets = st.assets.map { asset ->
                    if (asset.path == selected) {
                        asset.copy(textContent = newCode, sizeBytes = newCode.length)
                    } else asset
                }
                st.copy(
                    assets = updatedAssets,
                    previewRevision = st.previewRevision + 1,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                )
            }
        }
        scheduleAutoSave()
    }

    fun undoCode() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value.currentEditorCode
        redoStack.addLast(current)
        val prev = undoStack.removeLast()
        updateCode(prev, recordUndo = false)
    }

    fun redoCode() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value.currentEditorCode
        undoStack.addLast(current)
        val next = redoStack.removeLast()
        updateCode(next, recordUndo = false)
    }

    fun selectAssetTab(path: String?) {
        undoStack.clear()
        redoStack.clear()
        _uiState.update { it.copy(selectedAssetPath = path, canUndo = false, canRedo = false) }
    }

    // Multi-file manager operations
    fun createProjectFile(pathInput: String) {
        val clean = pathInput.trim().replace("\\", "/").trimStart('/')
        if (clean.isEmpty() || clean.equals("index.html", true)) return
        if (_uiState.value.assets.any { it.path.equals(clean, true) }) return

        val ext = clean.substringAfterLast('.', "").lowercase(Locale.US)
        val starter = when (ext) {
            "css" -> "/* $clean */\nbody {\n  font-family: system-ui, sans-serif;\n}\n"
            "js" -> "// $clean\nconsole.log('$clean loaded');\n"
            "html", "htm" -> HtmlToolkit.defaultStarterHtml(_uiState.value.settings.language.isRtl)
            else -> ""
        }
        val mime = when (ext) {
            "css" -> "text/css"
            "js" -> "application/javascript"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            else -> "text/plain"
        }
        val newAsset = ProjectAsset(clean, mime, textContent = starter, sizeBytes = starter.toByteArray().size)
        _uiState.update {
            it.copy(
                assets = it.assets + newAsset,
                selectedAssetPath = clean,
                previewRevision = it.previewRevision + 1,
            )
        }
        scheduleAutoSave()
    }

    fun renameOrMoveFile(oldPath: String, newPathInput: String) {
        val newPath = newPathInput.trim().replace("\\", "/").trimStart('/')
        if (newPath.isEmpty() || oldPath == newPath) return
        _uiState.update { state ->
            val updated = state.assets.map { if (it.path == oldPath) it.copy(path = newPath) else it }
            state.copy(
                assets = updated,
                selectedAssetPath = if (state.selectedAssetPath == oldPath) newPath else state.selectedAssetPath,
                previewRevision = state.previewRevision + 1,
            )
        }
        scheduleAutoSave()
    }

    fun deleteProjectFile(path: String) {
        _uiState.update { state ->
            state.copy(
                assets = state.assets.filterNot { it.path == path },
                selectedAssetPath = if (state.selectedAssetPath == path) null else state.selectedAssetPath,
                previewRevision = state.previewRevision + 1,
            )
        }
        scheduleAutoSave()
    }

    fun addAssetFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { ZipArchiveManager.readAssetFromUri(context, uri) }
            }.onSuccess { asset ->
                _uiState.update { st ->
                    val filtered = st.assets.filterNot { it.path.equals(asset.path, true) }
                    st.copy(assets = filtered + asset, previewRevision = st.previewRevision + 1)
                }
                scheduleAutoSave()
                emitMessage("${asset.path} ✓")
            }.onFailure { err ->
                emitMessage(err.localizedMessage ?: "Failed to add file")
            }
        }
    }

    fun updateProjectName(name: String) {
        _uiState.update { state ->
            val slug = HtmlToolkit.toSafePackageSegment(name)
            state.copy(
                projectName = name,
                appConfig = state.appConfig.copy(
                    appName = name.ifBlank { state.appConfig.appName },
                    packageName = "com.webforge.$slug",
                ),
            )
        }
        scheduleAutoSave()
    }

    fun pasteHtml(text: String) {
        if (text.isBlank()) {
            emitMessage(strings().clipboardEmptyMsg)
            return
        }
        val title = HtmlToolkit.extractTitle(text)
        _uiState.update { state ->
            val updatedConfig = if (title != null) {
                state.appConfig.copy(
                    appName = title,
                    splashTitle = state.appConfig.splashTitle.ifBlank { title },
                )
            } else state.appConfig
            state.copy(
                selectedAssetPath = null,
                htmlContent = text,
                projectName = title ?: state.projectName,
                appConfig = updatedConfig,
                previewRevision = state.previewRevision + 1,
            )
        }
        scheduleLintDetection(text)
        emitMessage(strings().pastedMsg)
        scheduleAutoSave()
    }

    fun applyTemplate(template: TemplateItem) {
        val isAr = _uiState.value.settings.language.isRtl
        val name = if (isAr) template.titleAr else template.titleEn
        val slug = HtmlToolkit.toSafePackageSegment(template.id)
        _uiState.update {
            it.copy(
                activeProjectId = 0L,
                projectName = name,
                htmlContent = template.html,
                assets = emptyList(),
                selectedAssetPath = null,
                publishedUrl = null,
                appConfig = AppExportConfig(appName = name, packageName = "com.webforge.$slug"),
                previewRevision = it.previewRevision + 1,
            )
        }
        scheduleLintDetection(template.html)
        scheduleAutoSave()
    }

    fun createBlankNewProject() {
        val isAr = _uiState.value.settings.language.isRtl
        val html = HtmlToolkit.defaultStarterHtml(isAr)
        val name = if (isAr) "مشروع جديد" else "New Project"
        _uiState.update {
            it.copy(
                activeProjectId = 0L,
                projectName = name,
                htmlContent = html,
                assets = emptyList(),
                selectedAssetPath = null,
                publishedUrl = null,
                isFavorite = false,
                appConfig = AppExportConfig(appName = name),
                previewRevision = it.previewRevision + 1,
            )
        }
        scheduleLintDetection(html)
    }

    fun clearCode() = updateCode("")

    fun importFileFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            runCatching {
                withContext(Dispatchers.IO) { ZipArchiveManager.importFromUri(context, uri) }
            }.onSuccess { imported ->
                val slug = HtmlToolkit.toSafePackageSegment(imported.suggestedName)
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        activeProjectId = 0L,
                        projectName = imported.suggestedName,
                        htmlContent = imported.entryHtml,
                        assets = imported.assets,
                        selectedAssetPath = null,
                        publishedUrl = null,
                        appConfig = it.appConfig.copy(appName = imported.suggestedName, packageName = "com.webforge.$slug"),
                        previewRevision = it.previewRevision + 1,
                    )
                }
                scheduleLintDetection(imported.entryHtml)
                saveCurrentProjectInternal("Imported file")
                emitMessage("${imported.suggestedName} (${imported.assets.size + 1} ${strings().assetsCountLabel})")
            }.onFailure { err ->
                _uiState.update { it.copy(isImporting = false) }
                emitMessage(err.localizedMessage ?: "Import failed")
            }
        }
    }

    fun importFromUrl(url: String) {
        if (_uiState.value.isEffectiveOffline) {
            emitMessage(strings().offlineDeployBlocked)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            runCatching { WebImporter.importFromUrl(url) }
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            activeProjectId = 0L,
                            projectName = res.suggestedName,
                            htmlContent = res.entryHtml,
                            assets = emptyList(),
                            selectedAssetPath = null,
                            appConfig = it.appConfig.copy(appName = res.suggestedName),
                            previewRevision = it.previewRevision + 1,
                        )
                    }
                    scheduleLintDetection(res.entryHtml)
                    saveCurrentProjectInternal("Imported URL")
                    emitMessage("${res.suggestedName} ✓")
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isImporting = false) }
                    emitMessage(err.localizedMessage ?: "URL import failed")
                }
        }
    }

    fun importFromGitHub(owner: String, repo: String, branch: String, token: String) {
        if (_uiState.value.isEffectiveOffline) {
            emitMessage(strings().offlineDeployBlocked)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            runCatching { WebImporter.importFromGitHubRepo(owner, repo, branch, token) }
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            activeProjectId = 0L,
                            projectName = res.suggestedName,
                            htmlContent = res.entryHtml,
                            assets = res.assets,
                            selectedAssetPath = null,
                            appConfig = it.appConfig.copy(appName = res.suggestedName),
                            previewRevision = it.previewRevision + 1,
                        )
                    }
                    scheduleLintDetection(res.entryHtml)
                    saveCurrentProjectInternal("Imported GitHub")
                    emitMessage("${res.suggestedName} (${res.assets.size + 1} ${strings().assetsCountLabel})")
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isImporting = false) }
                    emitMessage(err.localizedMessage ?: "GitHub import failed")
                }
        }
    }

    // Code Tools
    fun expandEmmet(abbr: String) {
        val expanded = CodeLinterAndAudit.expandEmmet(abbr)
        if (expanded.isNotBlank()) {
            updateCode(_uiState.value.currentEditorCode + "\n" + expanded)
        }
    }

    fun applyLintFix(fixType: String) {
        val state = _uiState.value
        val fixed = CodeLinterAndAudit.applyCodeFix(state.htmlContent, fixType, state.appConfig.appName)
        _uiState.update {
            it.copy(
                htmlContent = fixed,
                codeIssues = CodeLinterAndAudit.detectCodeIssues(fixed),
                previewRevision = it.previewRevision + 1,
            )
        }
        scheduleAutoSave()
    }

    fun formatCurrentCode() {
        val state = _uiState.value
        if (state.selectedAssetPath == null) {
            updateCode(HtmlToolkit.formatHtml(state.htmlContent))
        }
        emitMessage(strings().formattedMsg)
    }

    fun minifyProjectCode() {
        val state = _uiState.value
        val result = HtmlToolkit.minifyHtml(state.htmlContent)
        val minifiedAssets = state.assets.map { asset ->
            when {
                asset.path.endsWith(".css", true) && asset.textContent != null -> {
                    val css = HtmlToolkit.minifyCss(asset.textContent)
                    asset.copy(textContent = css, sizeBytes = css.toByteArray().size)
                }
                asset.path.endsWith(".js", true) && asset.textContent != null -> {
                    val js = HtmlToolkit.minifyJs(asset.textContent)
                    asset.copy(textContent = js, sizeBytes = js.toByteArray().size)
                }
                else -> asset
            }
        }
        _uiState.update {
            it.copy(
                htmlContent = result.code,
                assets = minifiedAssets,
                codeIssues = CodeLinterAndAudit.detectCodeIssues(result.code),
                previewRevision = it.previewRevision + 1,
            )
        }
        emitMessage(String.format(strings().bytesSavedMsg, result.savedPercent))
        scheduleAutoSave()
    }

    fun fixRelativeLinks() {
        val state = _uiState.value
        val (fixed, count) = HtmlToolkit.fixRelativeLinks(state.htmlContent, state.assets.map { it.path })
        updateCode(fixed)
        emitMessage(String.format(strings().linksFixedMsg, count))
    }

    fun injectSeoViewportAndIcon() {
        val state = _uiState.value
        val enhanced = HtmlToolkit.injectViewportSeoAndIcon(
            rawHtml = state.htmlContent,
            title = state.appConfig.appName.ifBlank { state.projectName },
            description = state.appConfig.seoDescription,
            themeColorHex = state.appConfig.themeColorHex,
            hasFavicon = true,
        )
        updateCode(enhanced)
        emitMessage(strings().seoAddedMsg)
    }

    fun injectPwaSupport() {
        updateCode(HtmlToolkit.injectPwaLinks(_uiState.value.htmlContent))
        emitMessage(strings().exportedSuccess)
    }

    fun injectTrackingAndCookies(gaId: String, adsenseId: String, includeBanner: Boolean) {
        val updated = CodeLinterAndAudit.injectTrackingAndCookieBanner(
            html = _uiState.value.htmlContent,
            analyticsId = gaId.trim(),
            adsensePubId = adsenseId.trim(),
            includeCookieBanner = includeBanner,
        )
        updateCode(updated)
        emitMessage(strings().savedSuccess)
    }

    fun runSiteAudit() {
        val st = _uiState.value
        val report = CodeLinterAndAudit.auditWebsite(st.htmlContent, st.assets)
        _uiState.update { it.copy(auditReport = report) }
    }

    // Preview
    fun selectDeviceFrame(frame: DeviceFrame) = _uiState.update { it.copy(selectedDevice = frame) }
    fun togglePreviewOrientation() = _uiState.update { it.copy(isLandscapePreview = !it.isLandscapePreview) }
    fun reloadPreview() = _uiState.update { it.copy(consoleLogs = emptyList(), previewRevision = it.previewRevision + 1) }

    fun appendConsoleLog(level: String, message: String) {
        _uiState.update { st -> st.copy(consoleLogs = (st.consoleLogs + ConsoleEntry(level, message)).takeLast(40)) }
    }

    // App Export Configuration
    fun updateAppConfig(transform: (AppExportConfig) -> AppExportConfig) {
        _uiState.update { st -> st.copy(appConfig = transform(st.appConfig)) }
        scheduleAutoSave()
    }

    fun setCustomIconFromUri(uri: Uri) {
        viewModelScope.launch {
            val base64 = withContext(Dispatchers.IO) { ZipArchiveManager.encodeImageUriToBase64Png(context, uri) }
            if (base64 != null) updateAppConfig { it.copy(customIconBase64 = base64) }
        }
    }

    fun resetDefaultIcon() = updateAppConfig { it.copy(customIconBase64 = null) }

    // Exports
    fun exportWebsiteZip(includePwa: Boolean, saveViaSaf: Boolean) {
        viewModelScope.launch {
            val st = _uiState.value
            val bytes = withContext(Dispatchers.IO) {
                ZipArchiveManager.buildWebsiteZip(context, st.projectName, st.htmlContent, st.assets, st.appConfig, includePwa)
            }
            val fileName = "${HtmlToolkit.slugify(st.projectName)}${if (includePwa) "-pwa" else "-website"}.zip"
            dispatchExport(fileName, "application/zip", bytes, saveViaSaf)
        }
    }

    fun exportSingleSelfContainedHtml(saveViaSaf: Boolean) {
        viewModelScope.launch {
            val st = _uiState.value
            val bundled = withContext(Dispatchers.Default) {
                CodeLinterAndAudit.bundleSingleSelfContainedHtml(st.htmlContent, st.assets)
            }
            val fileName = "${HtmlToolkit.slugify(st.projectName)}-standalone.html"
            dispatchExport(fileName, "text/html", bundled.toByteArray(Charsets.UTF_8), saveViaSaf)
        }
    }

    fun buildDirectApk(saveViaSaf: Boolean) {
        if (_uiState.value.isBuildingApk) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBuildingApk = true) }
            val st = _uiState.value
            runCatching {
                withContext(Dispatchers.IO) {
                    OnDeviceApkBuilder.buildSignedApk(
                        context = context,
                        projectName = st.projectName,
                        html = st.htmlContent,
                        assets = st.assets,
                        config = st.appConfig,
                    )
                }
            }.onSuccess { apkBytes ->
                _uiState.update { it.copy(isBuildingApk = false) }
                val safeFile = HtmlToolkit.safeFileName(st.appConfig.appName.ifBlank { st.projectName })
                dispatchExport("$safeFile.apk", "application/vnd.android.package-archive", apkBytes, saveViaSaf)
                emitMessage(strings().apkBuiltSuccessMsg)
            }.onFailure { err ->
                _uiState.update { it.copy(isBuildingApk = false) }
                emitMessage(err.localizedMessage ?: "APK build failed")
            }
        }
    }

    fun exportIosWebClip(saveViaSaf: Boolean) {
        viewModelScope.launch {
            val st = _uiState.value
            runCatching {
                withContext(Dispatchers.IO) {
                    OnDeviceApkBuilder.buildIosWebClipMobileConfig(
                        context = context,
                        projectName = st.projectName,
                        html = st.htmlContent,
                        assets = st.assets,
                        config = st.appConfig,
                        publishedUrl = st.publishedUrl,
                    )
                }
            }.onSuccess { bytes ->
                val safeFile = HtmlToolkit.safeFileName(st.appConfig.appName.ifBlank { st.projectName })
                dispatchExport("$safeFile-ios-app.mobileconfig", "application/x-apple-aspen-config", bytes, saveViaSaf)
            }.onFailure { err ->
                emitMessage(err.localizedMessage ?: "iOS profile export failed")
            }
        }
    }

    fun exportAndroidWebViewProject(saveViaSaf: Boolean) {
        viewModelScope.launch {
            val st = _uiState.value
            val bytes = withContext(Dispatchers.IO) {
                ZipArchiveManager.buildAndroidWebViewProjectZip(context, st.projectName, st.htmlContent, st.assets, st.appConfig)
            }
            val fileName = "${HtmlToolkit.slugify(st.appConfig.appName)}-android-capacitor.zip"
            dispatchExport(fileName, "application/zip", bytes, saveViaSaf)
        }
    }

    fun exportIosIpa(saveViaSaf: Boolean) {
        viewModelScope.launch {
            val st = _uiState.value
            runCatching {
                withContext(Dispatchers.IO) {
                    OnDeviceApkBuilder.buildIosIpaArchive(
                        context = context,
                        projectName = st.projectName,
                        html = st.htmlContent,
                        assets = st.assets,
                        config = st.appConfig,
                    )
                }
            }.onSuccess { bytes ->
                val safeFile = HtmlToolkit.safeFileName(st.appConfig.appName.ifBlank { st.projectName })
                dispatchExport("$safeFile.ipa", "application/octet-stream", bytes, saveViaSaf)
                emitMessage(strings().exportedSuccess)
            }.onFailure { err ->
                emitMessage(err.localizedMessage ?: "IPA build failed")
            }
        }
    }

    fun exportIosCapacitorZip(saveViaSaf: Boolean) {
        viewModelScope.launch {
            val st = _uiState.value
            val bytes = withContext(Dispatchers.IO) {
                AssetAndStoreGenerator.buildIosCapacitorZip(context, st.projectName, st.htmlContent, st.assets, st.appConfig)
            }
            dispatchExport("${HtmlToolkit.slugify(st.appConfig.appName)}-ios-xcode-ipa-workflow.zip", "application/zip", bytes, saveViaSaf)
        }
    }

    fun exportElectronDesktopZip(saveViaSaf: Boolean) {
        viewModelScope.launch {
            val st = _uiState.value
            val bytes = withContext(Dispatchers.IO) {
                AssetAndStoreGenerator.buildElectronDesktopZip(context, st.projectName, st.htmlContent, st.assets, st.appConfig)
            }
            dispatchExport("${HtmlToolkit.slugify(st.appConfig.appName)}-electron-desktop.zip", "application/zip", bytes, saveViaSaf)
        }
    }

    fun exportKeystoreGuideZip(saveViaSaf: Boolean) {
        viewModelScope.launch {
            val st = _uiState.value
            val bytes = withContext(Dispatchers.Default) { AssetAndStoreGenerator.generateKeystoreGuideZip(st.appConfig) }
            dispatchExport("${HtmlToolkit.slugify(st.appConfig.appName)}-keystore-setup.zip", "application/zip", bytes, saveViaSaf)
        }
    }

    fun exportIconSplashPackZip(saveViaSaf: Boolean) {
        viewModelScope.launch {
            val st = _uiState.value
            val bytes = withContext(Dispatchers.IO) { AssetAndStoreGenerator.generateIconAndSplashPackZip(context, st.appConfig) }
            dispatchExport("${HtmlToolkit.slugify(st.appConfig.appName)}-icons-splash.zip", "application/zip", bytes, saveViaSaf)
        }
    }

    fun exportStorePackZip(includeAiDescriptions: Boolean, saveViaSaf: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingStorePack = true) }
            val st = _uiState.value
            val descriptions = if (includeAiDescriptions && !st.isEffectiveOffline) {
                runCatching {
                    geminiAssistant.generateStoreDescriptions(st.appConfig.appName, st.htmlContent)
                }.getOrNull()
            } else null

            val bytes = withContext(Dispatchers.IO) {
                AssetAndStoreGenerator.generateStorePackZip(context, st.appConfig, descriptions)
            }
            _uiState.update { it.copy(isGeneratingStorePack = false) }
            dispatchExport("${HtmlToolkit.slugify(st.appConfig.appName)}-store-pack.zip", "application/zip", bytes, saveViaSaf)
        }
    }

    fun exportQrPng(url: String, saveViaSaf: Boolean) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.Default) { QrCodeGenerator.generatePngBytes(url) }
            dispatchExport("${HtmlToolkit.slugify(_uiState.value.projectName)}-qr.png", "image/png", bytes, saveViaSaf)
        }
    }

    fun compressImageUriToWebP(uri: Uri, quality: Int) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { AssetAndStoreGenerator.compressImageToWebP(context, uri, quality) }
            }.onSuccess { res ->
                _uiState.update { it.copy(lastWebPResult = res) }
            }.onFailure { err ->
                emitMessage(err.localizedMessage ?: "Compression failed")
            }
        }
    }

    fun addLastWebPToProjectAssets() {
        val res = _uiState.value.lastWebPResult ?: return
        val asset = ProjectAsset(
            path = "images/${res.fileName}",
            mimeType = "image/webp",
            base64Content = Base64.encodeToString(res.webpData, Base64.NO_WRAP),
            sizeBytes = res.compressedBytes,
        )
        _uiState.update { st ->
            st.copy(assets = st.assets + asset, previewRevision = st.previewRevision + 1)
        }
        scheduleAutoSave()
        emitMessage("${asset.path} ✓")
    }

    private suspend fun dispatchExport(fileName: String, mimeType: String, bytes: ByteArray, saveViaSaf: Boolean) {
        if (saveViaSaf) {
            _events.emit(UiEvent.SaveBytesToSaf(fileName, mimeType, bytes))
        } else {
            val temp = withContext(Dispatchers.IO) { ZipArchiveManager.saveTempExportFile(context, fileName, bytes) }
            _events.emit(UiEvent.ShareFile(temp, mimeType, fileName))
        }
    }

    // Hosting Deployment (5 Providers)
    fun deployWebsite(
        provider: HostingProvider,
        token: String,
        primaryParam: String,
        secondaryParam: String = "",
        saveTokenLocally: Boolean = true,
    ) {
        val state = _uiState.value
        if (state.isEffectiveOffline) {
            _uiState.update { it.copy(deployState = DeployState.Error(strings().offlineDeployBlocked)) }
            return
        }

        viewModelScope.launch {
            if (saveTokenLocally && token.isNotBlank()) {
                settingsRepo.saveProviderCredentials(provider, token, primaryParam)
            }
            _uiState.update { it.copy(deployState = DeployState.InProgress("Preparing bundle...")) }
            runCatching {
                val fileMap = withContext(Dispatchers.IO) {
                    ZipArchiveManager.buildWebsiteFileMap(
                        context, state.projectName, state.htmlContent, state.assets, state.appConfig, includePwa = true,
                    )
                }
                when (provider) {
                    HostingProvider.FREE_INSTANT -> {
                        val zipBytes = withContext(Dispatchers.IO) { ZipArchiveManager.zipFileMap(fileMap) }
                        val singleHtml = withContext(Dispatchers.Default) {
                            val bundled = CodeLinterAndAudit.bundleSingleSelfContainedHtml(state.htmlContent, state.assets)
                            val withSeo = HtmlToolkit.injectViewportSeoAndIcon(
                                rawHtml = bundled,
                                title = state.appConfig.appName.ifBlank { state.projectName },
                                description = state.appConfig.seoDescription,
                                themeColorHex = state.appConfig.themeColorHex,
                                hasFavicon = true,
                            )
                            HtmlToolkit.injectPwaLinks(withSeo)
                        }
                        deployer.deployFreeInstant(
                            singleHtml = singleHtml,
                            zipBytes = zipBytes,
                            onProgress = { s -> _uiState.update { it.copy(deployState = DeployState.InProgress(s)) } },
                        )
                    }
                    HostingProvider.GITHUB_PAGES -> deployer.deployToGitHubPages(
                        token = token,
                        owner = primaryParam,
                        repo = secondaryParam.ifBlank { HtmlToolkit.slugify(state.projectName) },
                        files = fileMap,
                        onProgress = { s -> _uiState.update { it.copy(deployState = DeployState.InProgress(s)) } },
                    )
                    HostingProvider.NETLIFY -> {
                        val zipBytes = withContext(Dispatchers.IO) { ZipArchiveManager.zipFileMap(fileMap) }
                        deployer.deployToNetlify(
                            token = token,
                            siteIdInput = primaryParam,
                            siteName = state.projectName,
                            zipBytes = zipBytes,
                            onProgress = { s -> _uiState.update { it.copy(deployState = DeployState.InProgress(s)) } },
                        )
                    }
                    HostingProvider.VERCEL -> deployer.deployToVercel(
                        token = token,
                        projectName = state.projectName,
                        files = fileMap,
                        onProgress = { s -> _uiState.update { it.copy(deployState = DeployState.InProgress(s)) } },
                    )
                    HostingProvider.CLOUDFLARE_PAGES -> deployer.deployToCloudflarePages(
                        token = token,
                        accountId = primaryParam,
                        projectName = state.projectName,
                        files = fileMap,
                        onProgress = { s -> _uiState.update { it.copy(deployState = DeployState.InProgress(s)) } },
                    )
                    HostingProvider.FIREBASE_HOSTING -> deployer.deployToFirebaseHosting(
                        oauthAccessToken = token,
                        siteId = primaryParam,
                        files = fileMap,
                        onProgress = { s -> _uiState.update { it.copy(deployState = DeployState.InProgress(s)) } },
                    )
                }
            }.onSuccess { liveUrl ->
                _uiState.update {
                    it.copy(
                        publishedUrl = liveUrl,
                        lastProvider = provider,
                        deployState = DeployState.Success(liveUrl, provider),
                    )
                }
                saveCurrentProjectInternal("Published to ${provider.title}")
            }.onFailure { err ->
                _uiState.update {
                    it.copy(deployState = DeployState.Error(err.message ?: "Deployment failed"))
                }
            }
        }
    }

    fun publishFreeWithoutToken() {
        deployWebsite(
            provider = HostingProvider.FREE_INSTANT,
            token = "",
            primaryParam = "",
            secondaryParam = "",
            saveTokenLocally = false,
        )
    }

    fun resetDeployState() = _uiState.update { it.copy(deployState = DeployState.Idle) }

    // Gemini AI Studio Assistant
    fun generateSiteFromPrompt(prompt: String, mobileAppMode: Boolean = false) {
        if (prompt.isBlank()) return
        if (_uiState.value.isEffectiveOffline) {
            _uiState.update { it.copy(aiTaskState = AiTaskState.Error(strings().offlineDeployBlocked)) }
            return
        }
        val preferAr = _uiState.value.settings.language.isRtl
        viewModelScope.launch {
            _uiState.update { it.copy(aiTaskState = AiTaskState.Loading) }
            try {
                var lastChunk = ""
                geminiAssistant.streamGenerateWebsite(prompt, preferAr, mobileAppMode).collect { partial ->
                    lastChunk = partial
                    _uiState.update { it.copy(aiTaskState = AiTaskState.Streaming(partial)) }
                }
                val clean = lastChunk.trim()
                    .removePrefix("```html").removePrefix("```").removeSuffix("```").trim()
                if (clean.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            aiTaskState = AiTaskState.Success(
                                text = if (preferAr) {
                                    if (mobileAppMode) "تم بناء تطبيق HTML5 بنجاح! اختر المعاينة الحية أو بناء APK مباشرة."
                                    else "تم توليد الموقع بنجاح! اختر المعاينة الحية أو تطبيق الكود."
                                } else {
                                    if (mobileAppMode) "HTML5 app built! Tap Apply & Preview or Build APK."
                                    else "Website generated! Tap Apply to use or preview it."
                                },
                                proposedCode = clean,
                            ),
                        )
                    }
                } else {
                    _uiState.update { it.copy(aiTaskState = AiTaskState.Error("Gemini returned empty output.")) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(aiTaskState = AiTaskState.Error(e.localizedMessage ?: "AI generation failed")) }
            }
        }
    }

    fun runAiQuickAction(action: AiQuickAction) {
        val st = _uiState.value
        if (st.isEffectiveOffline) {
            _uiState.update { it.copy(aiTaskState = AiTaskState.Error(strings().offlineDeployBlocked)) }
            return
        }
        val preferAr = st.settings.language.isRtl
        viewModelScope.launch {
            _uiState.update { it.copy(aiTaskState = AiTaskState.Loading) }
            runCatching {
                geminiAssistant.runQuickTransform(action, st.currentEditorCode, preferAr)
            }.onSuccess { (summary, proposed) ->
                _uiState.update { it.copy(aiTaskState = AiTaskState.Success(summary, proposed)) }
            }.onFailure { err ->
                _uiState.update { it.copy(aiTaskState = AiTaskState.Error(err.localizedMessage ?: "AI action failed")) }
            }
        }
    }

    fun sendAiChatMessage(message: String) {
        if (message.isBlank()) return
        val st = _uiState.value
        if (st.isEffectiveOffline) {
            _uiState.update { it.copy(aiTaskState = AiTaskState.Error(strings().offlineDeployBlocked)) }
            return
        }
        val preferAr = st.settings.language.isRtl
        _uiState.update {
            it.copy(
                aiChatHistory = it.aiChatHistory + AiChatMessage(isUser = true, text = message),
                aiTaskState = AiTaskState.Loading,
            )
        }
        viewModelScope.launch {
            runCatching {
                geminiAssistant.chatEditProject(message, st.currentEditorCode, preferAr)
            }.onSuccess { (reply, proposedCode) ->
                _uiState.update {
                    it.copy(
                        aiChatHistory = it.aiChatHistory + AiChatMessage(
                            isUser = false,
                            text = reply,
                            proposedCode = proposedCode,
                        ),
                        aiTaskState = AiTaskState.Success(reply, proposedCode),
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(aiTaskState = AiTaskState.Error(err.localizedMessage ?: "AI chat failed"))
                }
            }
        }
    }

    fun applyAiProposedCode(proposedCode: String) {
        saveCurrentProject(withSnapshot = true)
        updateCode(proposedCode)
        _uiState.update { st ->
            st.copy(
                aiChatHistory = st.aiChatHistory.map { msg ->
                    if (msg.proposedCode == proposedCode) msg.copy(isApplied = true) else msg
                },
                aiTaskState = AiTaskState.Idle,
            )
        }
        emitMessage(strings().savedSuccess)
    }

    fun discardAiTask() {
        _uiState.update { it.copy(aiTaskState = AiTaskState.Idle) }
    }

    // Projects & Backup
    fun saveCurrentProject(withSnapshot: Boolean = false) {
        viewModelScope.launch {
            saveCurrentProjectInternal(if (withSnapshot) "Snapshot" else null)
            emitMessage(strings().savedSuccess)
        }
    }

    private fun scheduleAutoSave() {
        if (!_uiState.value.settings.autoSave) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1200)
            saveCurrentProjectInternal(null)
        }
    }

    private suspend fun saveCurrentProjectInternal(snapshotLabel: String?) {
        val st = _uiState.value
        val savedId = projectRepo.saveProject(
            id = st.activeProjectId,
            name = st.projectName,
            htmlContent = st.htmlContent,
            assets = st.assets,
            config = st.appConfig,
            publishedUrl = st.publishedUrl,
            lastProvider = st.lastProvider,
            isFavorite = st.isFavorite,
        )
        if (savedId != st.activeProjectId) {
            _uiState.update { it.copy(activeProjectId = savedId) }
            settingsRepo.setLastProjectId(savedId)
        }
        if (snapshotLabel != null) {
            projectRepo.saveSnapshot(savedId, snapshotLabel, st.htmlContent, st.assets)
        }
    }

    fun reopenProject(projectId: Long) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { projectRepo.loadProject(projectId) } ?: return@launch
            _uiState.update {
                it.copy(
                    activeProjectId = loaded.id,
                    projectName = loaded.name,
                    htmlContent = loaded.htmlContent,
                    assets = loaded.assets,
                    selectedAssetPath = null,
                    appConfig = loaded.appConfig,
                    publishedUrl = loaded.publishedUrl,
                    lastProvider = loaded.lastProvider,
                    isFavorite = loaded.isFavorite,
                    previewRevision = it.previewRevision + 1,
                )
            }
            scheduleLintDetection(loaded.htmlContent)
            settingsRepo.setLastProjectId(loaded.id)
        }
    }

    fun toggleFavoriteProject(projectId: Long, currentFav: Boolean) {
        viewModelScope.launch {
            projectRepo.toggleFavorite(projectId, !currentFav)
            if (_uiState.value.activeProjectId == projectId) {
                _uiState.update { it.copy(isFavorite = !currentFav) }
            }
        }
    }

    fun restoreSnapshot(snapshotId: Long) {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) { projectRepo.restoreSnapshot(snapshotId) } ?: return@launch
            _uiState.update {
                it.copy(
                    htmlContent = restored.first,
                    assets = restored.second,
                    selectedAssetPath = null,
                    previewRevision = it.previewRevision + 1,
                )
            }
            scheduleLintDetection(restored.first)
            scheduleAutoSave()
        }
    }

    fun duplicateProject(projectId: Long) {
        viewModelScope.launch { projectRepo.duplicateProject(projectId) }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            projectRepo.deleteProject(projectId)
            if (_uiState.value.activeProjectId == projectId) {
                _uiState.update { it.copy(activeProjectId = 0L) }
                settingsRepo.setLastProjectId(0L)
            }
        }
    }

    fun exportFullBackup(saveViaSaf: Boolean) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) { projectRepo.exportBackupJson() }
            dispatchExport("webforge-backup.json", "application/json", json.toByteArray(Charsets.UTF_8), saveViaSaf)
        }
    }

    fun importBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("Unable to read backup file")
                }
                projectRepo.importBackupJson(text)
            }.onSuccess { count ->
                emitMessage("Imported $count projects ✓")
            }.onFailure { err ->
                emitMessage(err.localizedMessage ?: "Backup import failed")
            }
        }
    }

    // Settings
    fun setLanguage(language: AppLanguage) { viewModelScope.launch { settingsRepo.setLanguage(language) } }
    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settingsRepo.setThemeMode(mode) } }
    fun setAutoSave(enabled: Boolean) { viewModelScope.launch { settingsRepo.setAutoSave(enabled) } }
    fun setOfflineMode(enabled: Boolean) { viewModelScope.launch { settingsRepo.setOfflineMode(enabled) } }
    fun setEditorFontSize(size: Int) { viewModelScope.launch { settingsRepo.setEditorFontSize(size) } }
    fun setEditorTheme(theme: EditorTheme) { viewModelScope.launch { settingsRepo.setEditorTheme(theme) } }
    fun clearSavedTokens() {
        viewModelScope.launch {
            settingsRepo.clearSavedTokens()
            emitMessage(strings().savedSuccess)
        }
    }
    fun clearAllData() {
        viewModelScope.launch {
            projectRepo.clearAllProjects()
            settingsRepo.clearAll()
            createBlankNewProject()
            emitMessage(strings().savedSuccess)
        }
    }

    fun emitMessage(text: String) {
        viewModelScope.launch { _events.emit(UiEvent.ShowMessage(text)) }
    }
}
