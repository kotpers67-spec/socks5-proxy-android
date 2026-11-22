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
import com.socks5proxy.app.network.Socks5Checker
import com.socks5proxy.app.service.LocalProxyService
import com.socks5proxy.app.service.Socks5VpnService
import com.socks5proxy.app.updater.UpdateInfo
import com.socks5proxy.app.updater.UpdateService
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class MainUiState(
    val config: ProxyConfig = ProxyConfig(),
    val installedApps: List<AppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val isCheckingPing: Boolean = false,
    val pingResult: PingResult? = null,
    val isAppSelectorOpen: Boolean = false,
    val isProfileEditorOpen: Boolean = false,
    val editingProfile: ProxyProfile? = null,
    val isVpnImportDialogOpen: Boolean = false,
    val isImportingVpn: Boolean = false,
    val vpnImportError: String? = null,
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
    val isPingingAll: Boolean = false
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

    init {
        loadInstalledApps()
        checkForUpdates()
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
        viewModelScope.launch {
            val packageInfo = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
            val currentVersion = packageInfo.versionName ?: "1.0.0"
            val info = UpdateService.checkForUpdates(currentVersion)
            val hasUpdate = info.hasUpdate

            val snoozedVer = sharedPrefs.getString("snoozed_update_version", "").orEmpty()
            val snoozedTime = sharedPrefs.getLong("snoozed_update_time", 0L)
            val isSnoozedActive = snoozedVer == info.latestVersion && (System.currentTimeMillis() - snoozedTime < 24 * 60 * 60 * 1000L)

            val showDialog = hasUpdate && (forceShowDialog || !isSnoozedActive)
            val existingPart = UpdateService.getExistingPartBytes(getApplication<Application>())

            _uiState.update {
                it.copy(
                    updateInfo = info.takeIf { u -> u.hasUpdate },
                    isUpdateDialogVisible = showDialog,
                    downloadedBytes = existingPart,
                    isResumingDownload = existingPart > 0
                )
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
        if (targetApk.isFile && targetApk.length() > 1024 * 1024) {
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

    fun setLocalListenPort(port: Int) = updateConfig { copy(localListenPort = port) }

    fun toggleAutoStart(enabled: Boolean) = updateConfig { copy(isAutoStartEnabled = enabled) }

    fun toggleFallbackDirect(enabled: Boolean) = updateConfig { copy(isFallbackDirectEnabled = enabled) }

    fun togglePerAppRouting(enabled: Boolean) = updateConfig { copy(isPerAppRoutingEnabled = enabled) }

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
            val finalProfiles = if (nextList.isEmpty()) listOf(ProxyProfile()) else nextList
            val nextActiveId = if (activeProfileId == profileId) finalProfiles.first().id else activeProfileId
            copy(profiles = finalProfiles, activeProfileId = nextActiveId)
        }
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
        val localStatus = LocalProxyService.statusFlow.value
        val checkLocalRoute = config.engineMode == ProxyEngineMode.LOCAL_PROXY &&
                localStatus.state == com.socks5proxy.app.model.TunnelState.CONNECTED &&
                localStatus.boundPort in 1..65535
        val target = if (checkLocalRoute) {
            ProxyProfile(
                name = "Локальный SOCKS5",
                host = "127.0.0.1",
                port = localStatus.boundPort
            )
        } else {
            config.activeProfile
        }
        if (!target.isValid || _uiState.value.isCheckingPing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingPing = true, pingResult = null) }
            val result = Socks5Checker.checkConnection(target)
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

            profiles.forEach { server ->
                launch {
                    val result = Socks5Checker.pingTcp(server.host, server.port)
                    _uiState.update { state ->
                        state.copy(
                            pingingServerIds = state.pingingServerIds - server.id,
                            serverPingMap = state.serverPingMap + (server.id to result)
                        )
                    }
                }
            }

            _uiState.update { it.copy(isPingingAll = false) }
        }
    }
}
