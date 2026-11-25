package com.socks5proxy.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.socks5proxy.app.data.AppListRepository
import com.socks5proxy.app.data.ProxyPreferences
import com.socks5proxy.app.data.VpnSubscriptionImporter
import com.socks5proxy.app.model.AppInfo
import com.socks5proxy.app.model.ProxyConfig
import com.socks5proxy.app.model.ProxyEngineMode
import com.socks5proxy.app.model.ProxyProfile
import com.socks5proxy.app.model.TunnelStatus
import com.socks5proxy.app.network.PingResult
import com.socks5proxy.app.network.PublicIpResult
import com.socks5proxy.app.network.Socks5Checker
import com.socks5proxy.app.service.LocalProxyService
import com.socks5proxy.app.service.Socks5VpnService
import com.socks5proxy.app.updater.UpdateInfo
import com.socks5proxy.app.updater.UpdateService
import com.socks5proxy.app.util.AppLogBuffer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Immutable
data class MainUiState(
    val config: ProxyConfig = ProxyConfig(),
    val installedApps: List<AppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val isCheckingPing: Boolean = false,
    val pingResult: PingResult? = null,
    val isCheckingPublicIp: Boolean = false,
    val publicIpResult: PublicIpResult? = null,
    val isAppSelectorOpen: Boolean = false,
    val isProfileEditorOpen: Boolean = false,
    val editingProfile: ProxyProfile? = null,
    val isVpnImportDialogOpen: Boolean = false,
    val isImportingVpn: Boolean = false,
    val isRefreshingSubscriptions: Boolean = false,
    val vpnImportError: String? = null,
    val currentAppVersion: String = "",
    val isCheckingUpdate: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isResumingDownload: Boolean = false,
    val isUpdateDialogVisible: Boolean = false,
    val isUpdateApkReady: Boolean = false,
    val isDarkTheme: Boolean = true,
    val activeTab: Int = 0,
    val toastMessage: String? = null,
    val serverPingMap: Map<String, PingResult> = emptyMap(),
    val pingingServerIds: Set<String> = emptySet(),
    val isPingingAll: Boolean = false,
    val isPingingAllSocks: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = ProxyPreferences(application)
    private val appRepo = AppListRepository(application)
    private val sharedPrefs = application.getSharedPreferences("socks5_proxy_prefs", Context.MODE_PRIVATE)

    private val initialDarkTheme = sharedPrefs.getBoolean("proxy_dark_theme", true)
    private var toastJob: Job? = null
    private var downloadedUpdateApk: File? = null

    private val _uiState = MutableStateFlow(
        MainUiState(
            config = prefs.loadConfig(),
            isDarkTheme = initialDarkTheme
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val tunnelStatus: StateFlow<TunnelStatus> = _uiState
        .map { it.config.engineMode }
        .distinctUntilChanged()
        .flatMapLatest { mode ->
            if (mode == ProxyEngineMode.LOCAL_PROXY) {
                LocalProxyService.statusFlow
            } else {
                Socks5VpnService.statusFlow
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TunnelStatus())

    val logsFlow: StateFlow<List<String>> = AppLogBuffer.logsFlow

    init {
        val currentVersion = runCatching {
            application.packageManager.getPackageInfo(application.packageName, 0).versionName ?: "1.0.0"
        }.getOrDefault("1.0.0")
        _uiState.update { it.copy(currentAppVersion = currentVersion) }
        UpdateService.cleanupOldUpdates(application)
        loadInstalledApps()
        if (_uiState.value.config.isAutoCheckUpdatesEnabled) {
            checkForUpdates(forceShowDialog = false)
        }
    }

    fun setDarkTheme(isDark: Boolean) {
        sharedPrefs.edit().putBoolean("proxy_dark_theme", isDark).apply()
        _uiState.update { it.copy(isDarkTheme = isDark) }
        showToast(if (isDark) "Включена тёмная тема" else "Включена светлая тема")
    }

    fun toggleDarkTheme() {
        setDarkTheme(!_uiState.value.isDarkTheme)
    }

    fun setActiveTab(tab: Int) {
        _uiState.update { it.copy(activeTab = tab.coerceIn(0, 2)) }
    }

    fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(2200)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    fun dismissToast() {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val apps = appRepo.getInstalledApps(_uiState.value.config.allowedApps)
            _uiState.update { it.copy(installedApps = apps, isLoadingApps = false) }
        }
    }

    fun checkForUpdates(forceShowDialog: Boolean = false) {
        if (_uiState.value.isCheckingUpdate) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true) }
            val context = getApplication<Application>()
            val currentVersion = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            }.getOrDefault("1.0.0")
            val info = UpdateService.checkForUpdates(currentVersion)
            val hasUpdate = info.hasUpdate

            val snoozedVer = sharedPrefs.getString("snoozed_update_version", "").orEmpty()
            val snoozedTime = sharedPrefs.getLong("snoozed_update_time", 0L)
            val isSnoozedActive = snoozedVer == info.latestVersion && (System.currentTimeMillis() - snoozedTime < 24 * 60 * 60 * 1000L)

            val showDialog = hasUpdate && (forceShowDialog || !isSnoozedActive)
            val existingPart = UpdateService.getExistingPartBytes(context)

            _uiState.update {
                it.copy(
                    currentAppVersion = currentVersion,
                    isCheckingUpdate = false,
                    updateInfo = info.takeIf { u -> u.hasUpdate },
                    isUpdateDialogVisible = showDialog,
                    downloadedBytes = existingPart,
                    isResumingDownload = existingPart > 0
                )
            }

            if (forceShowDialog && !hasUpdate) {
                val msg = if (!info.errorMessage.isNullOrBlank()) {
                    "Не удалось проверить обновления: ${info.errorMessage}"
                } else {
                    "У вас установлена актуальная версия v$currentVersion"
                }
                showToast(msg)
            }
        }
    }

    fun showUpdateDialog() {
        if (_uiState.value.updateInfo != null) {
            val existingPart = UpdateService.getExistingPartBytes(getApplication<Application>())
            _uiState.update {
                it.copy(
                    isUpdateDialogVisible = true,
                    downloadedBytes = existingPart,
                    isResumingDownload = existingPart > 0
                )
            }
        } else {
            checkForUpdates(forceShowDialog = true)
        }
    }

    fun downloadAndInstallUpdate() {
        if (_uiState.value.isDownloadingUpdate) return

        val context = getApplication<Application>()
        val info = _uiState.value.updateInfo ?: return
        val targetApk = UpdateService.getTargetApkFile(context, info.latestVersion)
        if (UpdateService.isApkValid(targetApk)) {
            openDownloadedUpdate(context, targetApk)
            return
        }

        _uiState.update { it.copy(isDownloadingUpdate = true) }
        viewModelScope.launch {
            try {
                val apk = UpdateService.downloadApk(context, info) { percent, downloaded, total ->
                    _uiState.update {
                        it.copy(
                            updateDownloadProgress = if (percent >= 0) percent else it.updateDownloadProgress,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            isResumingDownload = downloaded > 0
                        )
                    }
                }
                downloadedUpdateApk = apk
                _uiState.update { it.copy(isDownloadingUpdate = false, isUpdateApkReady = true) }
                openDownloadedUpdate(context, apk)
            } catch (error: Exception) {
                val downloadedPart = UpdateService.getExistingPartBytes(context)
                val partMb = downloadedPart / (1024 * 1024)
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        downloadedBytes = downloadedPart,
                        isResumingDownload = downloadedPart > 0
                    )
                }
                val msg = if (downloadedPart > 0) {
                    "Загрузка приостановлена на ${partMb} МБ. Нажмите «Возобновить», чтобы продолжить."
                } else {
                    error.message?.takeIf(String::isNotBlank) ?: "Не удалось скачать обновление"
                }
                showToast(msg)
            }
        }
    }

    private fun openDownloadedUpdate(context: Context, apk: File) {
        try {
            val opened = UpdateService.openInstaller(context, apk)
            showToast(
                if (opened) "Подтвердите установку в окне Android"
                else "Разрешите установку для приложения и нажмите кнопку ещё раз"
            )
        } catch (error: Exception) {
            showToast(error.message?.takeIf(String::isNotBlank) ?: "Не удалось открыть установщик Android")
        }
    }

    fun snoozeUpdate() {
        val info = _uiState.value.updateInfo
        if (info != null) {
            sharedPrefs.edit()
                .putString("snoozed_update_version", info.latestVersion)
                .putLong("snoozed_update_time", System.currentTimeMillis())
                .apply()
        }
        _uiState.update { it.copy(isUpdateDialogVisible = false) }
        showToast("Обновление отложено на 24 часа. Доступно в Настройках")
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(isUpdateDialogVisible = false) }
    }

    fun setEngineMode(mode: ProxyEngineMode) = updateConfig { copy(engineMode = mode) }

    fun setLocalListenPort(port: Int) {
        val safePort = port.coerceIn(1024, 65535)
        updateConfig { copy(localListenPort = safePort) }
        showToast("Локальный порт сохранён: $safePort")
    }

    fun toggleAutoStart(enabled: Boolean) = updateConfig { copy(isAutoStartEnabled = enabled) }

    fun toggleFallbackDirect(enabled: Boolean) = updateConfig { copy(isFallbackDirectEnabled = enabled) }

    fun togglePerAppRouting(enabled: Boolean) = updateConfig { copy(isPerAppRoutingEnabled = enabled) }

    fun toggleBypassLan(enabled: Boolean) = updateConfig { copy(isBypassLanEnabled = enabled) }

    fun toggleAutoCheckUpdates(enabled: Boolean) = updateConfig { copy(isAutoCheckUpdatesEnabled = enabled) }

    fun clearLogs() {
        AppLogBuffer.clear()
        showToast("Журнал событий очищен")
    }

    fun selectActiveProfile(profileId: String) = updateConfig { copy(activeProfileId = profileId) }

    fun selectActiveVpnProfile(profileId: String) = updateConfig { copy(activeVpnProfileId = profileId) }

    fun deleteVpnProfile(profileId: String) {
        updateConfig {
            val nextList = vpnProfiles.filter { it.id != profileId }
            val nextActiveId = if (activeVpnProfileId == profileId) nextList.firstOrNull()?.id ?: "" else activeVpnProfileId
            copy(vpnProfiles = nextList, activeVpnProfileId = nextActiveId)
        }
        _uiState.update { state ->
            state.copy(
                serverPingMap = state.serverPingMap - profileId,
                pingingServerIds = state.pingingServerIds - profileId
            )
        }
        showToast("VPN-сервер удалён")
    }

    fun clearAllVpnProfiles() {
        updateConfig {
            copy(vpnProfiles = emptyList(), activeVpnProfileId = "")
        }
        _uiState.update { state ->
            state.copy(
                serverPingMap = emptyMap(),
                pingingServerIds = emptySet()
            )
        }
        showToast("Все VPN-серверы удалены")
    }

    fun setDnsSettings(primaryDns: String, secondaryDns: String) {
        val pDns = primaryDns.trim().ifBlank { "1.1.1.1" }
        val sDns = secondaryDns.trim().ifBlank { "8.8.8.8" }
        updateConfig { copy(primaryDns = pDns, secondaryDns = sDns) }
        showToast("DNS сохранён: $pDns / $sDns")
    }

    fun setMtu(mtu: Int) {
        val safeMtu = mtu.coerceIn(1200, 1500)
        updateConfig { copy(mtu = safeMtu) }
        showToast("MTU сохранён: $safeMtu")
    }

    fun toggleVpnToSocksChain(enabled: Boolean) = updateConfig { copy(isVpnToSocksChainEnabled = enabled) }

    fun openVpnImportDialog() {
        _uiState.update { it.copy(isVpnImportDialogOpen = true, vpnImportError = null) }
    }

    fun closeVpnImportDialog() {
        _uiState.update { it.copy(isVpnImportDialogOpen = false, vpnImportError = null) }
    }

    fun importVpnServers(source: String) {
        if (_uiState.value.isImportingVpn) return
        _uiState.update { it.copy(isImportingVpn = true, vpnImportError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val imported = VpnSubscriptionImporter.import(source)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    updateConfig {
                        val importedSource = imported.firstOrNull()?.sourceUrl.orEmpty()
                        val retained = if (importedSource.isNotBlank()) {
                            vpnProfiles.filterNot { it.sourceUrl == importedSource }
                        } else {
                            vpnProfiles
                        }
                        val merged = (retained + imported).distinctBy { it.id }
                        val selectedStillExists = merged.any { it.id == activeVpnProfileId }
                        copy(
                            vpnProfiles = merged,
                            activeVpnProfileId = if (selectedStillExists) activeVpnProfileId else imported.first().id
                        )
                    }
                    _uiState.update { it.copy(isImportingVpn = false, isVpnImportDialogOpen = false) }
                    showToast("Добавлено серверов: ${imported.size}")
                }
            } catch (e: Exception) {
                val message = e.message?.takeIf(String::isNotBlank) ?: "Не удалось импортировать VPN-серверы"
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.update { it.copy(isImportingVpn = false, vpnImportError = message) }
                }
            }
        }
    }

    fun openAddProfile() {
        _uiState.update {
            it.copy(
                isProfileEditorOpen = true,
                editingProfile = ProxyProfile(name = "Прокси ${_uiState.value.config.profiles.size + 1}")
            )
        }
    }

    fun openEditProfile(profile: ProxyProfile) {
        _uiState.update { it.copy(isProfileEditorOpen = true, editingProfile = profile) }
    }

    fun closeProfileEditor() {
        _uiState.update { it.copy(isProfileEditorOpen = false, editingProfile = null) }
    }

    fun saveProfile(profile: ProxyProfile) {
        updateConfig {
            val existing = profiles.indexOfFirst { it.id == profile.id }
            val nextList = if (existing >= 0) {
                profiles.toMutableList().apply { set(existing, profile) }
            } else {
                profiles + profile
            }
            copy(
                profiles = nextList,
                activeProfileId = if (activeProfileId.isEmpty()) profile.id else activeProfileId
            )
        }
        closeProfileEditor()
    }

    fun deleteProfile(profileId: String) {
        updateConfig {
            val nextList = profiles.filter { it.id != profileId }
            val nextActiveId = if (activeProfileId == profileId) nextList.firstOrNull()?.id ?: "" else activeProfileId
            copy(profiles = nextList, activeProfileId = nextActiveId)
        }
        closeProfileEditor()
        showToast("Прокси-профиль удалён")
    }

    fun importSocksProfileFromText(rawText: String): Boolean {
        val parsed = ProxyProfile.fromUriOrLine(rawText) ?: return false
        saveProfile(parsed)
        showToast("Импортирован прокси ${parsed.host}:${parsed.port}")
        return true
    }

    fun toggleAppSelection(packageName: String) {
        val currentSet = _uiState.value.config.allowedApps
        val updatedSet = if (currentSet.contains(packageName)) currentSet - packageName else currentSet + packageName
        updateAllowedApps(updatedSet)
    }

    fun selectAllApps() {
        val allPkgs = _uiState.value.installedApps.map { it.packageName }.toSet()
        updateAllowedApps(allPkgs)
    }

    fun clearAllApps() {
        updateAllowedApps(emptySet())
    }

    private fun updateAllowedApps(apps: Set<String>) {
        updateConfig { copy(allowedApps = apps) }
        viewModelScope.launch(Dispatchers.IO) {
            prefs.saveAllowedApps(apps)
        }
    }

    private fun updateConfig(transform: ProxyConfig.() -> ProxyConfig) {
        _uiState.update { current ->
            val newConfig = current.config.transform()
            prefs.saveConfig(newConfig)
            current.copy(config = newConfig)
        }
    }

    fun checkProxyPing() {
        val config = _uiState.value.config
        val isTunnelConnected = tunnelStatus.value.state == com.socks5proxy.app.model.TunnelState.CONNECTED
        if (_uiState.value.isCheckingPing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingPing = true, pingResult = null) }
            val result = if (isTunnelConnected && config.engineMode != ProxyEngineMode.LOCAL_PROXY) {
                // В режиме системного VPN или VPN-профиля трафик устройства идет через туннель:
                // замеряем реальный доступ в сеть через HTTP 204
                Socks5Checker.pingHttp()
            } else if (isTunnelConnected && config.engineMode == ProxyEngineMode.LOCAL_PROXY) {
                val boundPort = if (tunnelStatus.value.boundPort > 0) tunnelStatus.value.boundPort else config.localListenPort
                val localTarget = ProxyProfile(name = "Локальный SOCKS5", host = "127.0.0.1", port = boundPort)
                Socks5Checker.checkConnection(localTarget)
            } else {
                // Если туннель выключен, проверяем активный профиль SOCKS или VPN-сервер
                if (config.engineMode == ProxyEngineMode.VPN_PROFILE) {
                    val activeVpn = config.activeVpnProfile
                    if (activeVpn != null) {
                        Socks5Checker.pingTcp(activeVpn.host, activeVpn.port)
                    } else {
                        PingResult(false, 0, "VPN-сервер не выбран")
                    }
                } else {
                    if (config.activeProfile.isValid) {
                        Socks5Checker.checkConnection(config.activeProfile)
                    } else {
                        PingResult(false, 0, "SOCKS5-профиль не настроен")
                    }
                }
            }
            _uiState.update { it.copy(isCheckingPing = false, pingResult = result) }
        }
    }

    fun setAppSelectorVisible(visible: Boolean) {
        _uiState.update { it.copy(isAppSelectorOpen = visible) }
    }

    fun pingVpnProfile(profileId: String) {
        val server = _uiState.value.config.vpnProfiles.firstOrNull { it.id == profileId } ?: return
        if (_uiState.value.pingingServerIds.contains(profileId)) return

        viewModelScope.launch {
            _uiState.update { it.copy(pingingServerIds = it.pingingServerIds + profileId) }
            val result = Socks5Checker.pingTcp(server.host, server.port)
            _uiState.update { state ->
                state.copy(
                    pingingServerIds = state.pingingServerIds - profileId,
                    serverPingMap = state.serverPingMap + (profileId to result)
                )
            }
        }
    }

    fun pingSocksProfile(profileId: String) {
        val profile = _uiState.value.config.profiles.firstOrNull { it.id == profileId } ?: return
        if (!profile.isValid || _uiState.value.pingingServerIds.contains(profileId)) return

        viewModelScope.launch {
            _uiState.update { it.copy(pingingServerIds = it.pingingServerIds + profileId) }
            val result = Socks5Checker.pingTcp(profile.host, profile.port)
            _uiState.update { state ->
                state.copy(
                    pingingServerIds = state.pingingServerIds - profileId,
                    serverPingMap = state.serverPingMap + (profileId to result)
                )
            }
        }
    }

    fun pingAllVpnProfiles() {
        val profiles = _uiState.value.config.vpnProfiles
        if (profiles.isEmpty() || _uiState.value.isPingingAll) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPingingAll = true,
                    pingingServerIds = it.pingingServerIds + profiles.map { p -> p.id }.toSet()
                )
            }

            val semaphore = Semaphore(5)
            val jobs = profiles.map { server ->
                launch {
                    val result = semaphore.withPermit {
                        Socks5Checker.pingTcp(server.host, server.port)
                    }
                    _uiState.update { state ->
                        state.copy(
                            pingingServerIds = state.pingingServerIds - server.id,
                            serverPingMap = state.serverPingMap + (server.id to result)
                        )
                    }
                }
            }
            jobs.joinAll()
            _uiState.update { it.copy(isPingingAll = false) }
        }
    }

    fun pingAllSocksProfiles() {
        val profiles = _uiState.value.config.profiles.filter { it.isValid }
        if (profiles.isEmpty() || _uiState.value.isPingingAllSocks) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPingingAllSocks = true,
                    pingingServerIds = it.pingingServerIds + profiles.map { p -> p.id }.toSet()
                )
            }
            val semaphore = Semaphore(5)
            val jobs = profiles.map { profile ->
                launch {
                    val result = semaphore.withPermit {
                        Socks5Checker.pingTcp(profile.host, profile.port)
                    }
                    _uiState.update { state ->
                        state.copy(
                            pingingServerIds = state.pingingServerIds - profile.id,
                            serverPingMap = state.serverPingMap + (profile.id to result)
                        )
                    }
                }
            }
            jobs.joinAll()
            _uiState.update { it.copy(isPingingAllSocks = false) }
        }
    }

    fun sortVpnProfilesByPing() {
        val pingMap = _uiState.value.serverPingMap
        updateConfig {
            val sorted = vpnProfiles.sortedWith(
                compareBy<com.socks5proxy.app.model.VpnServerProfile> { server ->
                    val ping = pingMap[server.id]
                    when {
                        ping == null -> 1
                        !ping.isSuccess -> 2
                        else -> 0
                    }
                }.thenBy { server ->
                    pingMap[server.id]?.latencyMs ?: Long.MAX_VALUE
                }
            )
            copy(vpnProfiles = sorted)
        }
        showToast("Серверы отсортированы по задержке")
    }

    fun selectBestVpnProfile() {
        val pingMap = _uiState.value.serverPingMap
        val best = _uiState.value.config.vpnProfiles
            .filter { pingMap[it.id]?.isSuccess == true }
            .minByOrNull { pingMap[it.id]?.latencyMs ?: Long.MAX_VALUE }
        if (best == null) {
            showToast("Сначала нажмите «Пинг всех» для замера задержки")
            return
        }
        selectActiveVpnProfile(best.id)
        setEngineMode(ProxyEngineMode.VPN_PROFILE)
        showToast("Выбран быстрый узел: ${best.name} (${pingMap[best.id]?.latencyMs} мс)")
    }

    fun refreshVpnSubscriptions() {
        if (_uiState.value.isRefreshingSubscriptions) return
        val urls = _uiState.value.config.vpnProfiles
            .map { it.sourceUrl.trim() }
            .filter { it.startsWith("https://", ignoreCase = true) }
            .distinct()
        if (urls.isEmpty()) {
            showToast("Нет сохранённых HTTPS-подписок для обновления")
            return
        }
        _uiState.update { it.copy(isRefreshingSubscriptions = true) }
        viewModelScope.launch(Dispatchers.IO) {
            var updatedCount = 0
            var failedCount = 0
            for (url in urls) {
                try {
                    val imported = VpnSubscriptionImporter.import(url)
                    withContext(Dispatchers.Main) {
                        updateConfig {
                            val retained = vpnProfiles.filterNot { it.sourceUrl == url }
                            val merged = (retained + imported).distinctBy { it.id }
                            val selectedStillExists = merged.any { it.id == activeVpnProfileId }
                            copy(
                                vpnProfiles = merged,
                                activeVpnProfileId = if (selectedStillExists) activeVpnProfileId else merged.firstOrNull()?.id.orEmpty()
                            )
                        }
                    }
                    updatedCount += imported.size
                } catch (e: Exception) {
                    failedCount++
                    AppLogBuffer.log("SUB", "Ошибка обновления подписки $url: ${e.message}")
                }
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isRefreshingSubscriptions = false) }
                val msg = if (failedCount == 0) {
                    "Подписки обновлены: $updatedCount серверов"
                } else {
                    "Обновлено серверов: $updatedCount (ошибок подписок: $failedCount)"
                }
                showToast(msg)
            }
        }
    }

    fun checkPublicIp() {
        if (_uiState.value.isCheckingPublicIp) return
        val config = _uiState.value.config
        val status = tunnelStatus.value
        val localPort = if (status.state == com.socks5proxy.app.model.TunnelState.CONNECTED &&
            config.engineMode == ProxyEngineMode.LOCAL_PROXY
        ) {
            if (status.boundPort > 0) status.boundPort else config.localListenPort
        } else {
            null
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingPublicIp = true) }
            val result = Socks5Checker.checkPublicIp(localSocksPort = localPort)
            _uiState.update { it.copy(isCheckingPublicIp = false, publicIpResult = result) }
            if (!result.isSuccess) {
                showToast("Не удалось определить IP: ${result.message}")
            }
        }
    }
}
