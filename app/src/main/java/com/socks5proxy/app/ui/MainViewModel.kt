package com.socks5proxy.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.socks5proxy.app.data.AppListRepository
import com.socks5proxy.app.data.ProxyPreferences
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
    val updateInfo: UpdateInfo? = null,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Int = 0,
    val isDarkTheme: Boolean = true,
    val activeTab: Int = 0,
    val toastMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = ProxyPreferences(application)
    private val appRepo = AppListRepository(application)
    private val sharedPrefs = application.getSharedPreferences("socks5_proxy_prefs", Context.MODE_PRIVATE)

    private val initialDarkTheme = sharedPrefs.getBoolean("proxy_dark_theme", true)
    private var toastJob: Job? = null

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

    fun checkForUpdates() {
        viewModelScope.launch {
            val packageInfo = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
            val currentVersion = packageInfo.versionName ?: "1.0.0"
            val info = UpdateService.checkForUpdates(currentVersion)
            if (info.hasUpdate) {
                _uiState.update { it.copy(updateInfo = info) }
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val url = _uiState.value.updateInfo?.downloadUrl ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingUpdate = true, downloadProgress = 0) }
            UpdateService.downloadAndInstallApk(getApplication(), url) { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
            _uiState.update { it.copy(isDownloadingUpdate = false) }
        }
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(updateInfo = null) }
    }

    fun setEngineMode(mode: ProxyEngineMode) = updateConfig { copy(engineMode = mode) }

    fun setLocalListenPort(port: Int) = updateConfig { copy(localListenPort = port) }

    fun toggleAutoStart(enabled: Boolean) = updateConfig { copy(isAutoStartEnabled = enabled) }

    fun toggleFallbackDirect(enabled: Boolean) = updateConfig { copy(isFallbackDirectEnabled = enabled) }

    fun togglePerAppRouting(enabled: Boolean) = updateConfig { copy(isPerAppRoutingEnabled = enabled) }

    fun selectActiveProfile(profileId: String) = updateConfig { copy(activeProfileId = profileId) }

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
        val active = config.activeProfile
        if (!active.isValid || _uiState.value.isCheckingPing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingPing = true, pingResult = null) }
            val result = Socks5Checker.checkConnection(active)
            _uiState.update { it.copy(isCheckingPing = false, pingResult = result) }
        }
    }

    fun setAppSelectorVisible(visible: Boolean) {
        _uiState.update { it.copy(isAppSelectorOpen = visible) }
    }
}
