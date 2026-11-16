package com.socks5proxy.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.socks5proxy.app.data.AppListRepository
import com.socks5proxy.app.data.ProxyPreferences
import com.socks5proxy.app.model.AppInfo
import com.socks5proxy.app.model.ProxyConfig
import com.socks5proxy.app.model.TunnelStatus
import com.socks5proxy.app.network.PingResult
import com.socks5proxy.app.network.Socks5Checker
import com.socks5proxy.app.service.Socks5VpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val config: ProxyConfig = ProxyConfig(),
    val installedApps: List<AppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val isCheckingPing: Boolean = false,
    val pingResult: PingResult? = null,
    val isAppSelectorOpen: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = ProxyPreferences(application)
    private val appRepo = AppListRepository(application)

    private val _uiState = MutableStateFlow(MainUiState(config = prefs.loadConfig()))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val tunnelStatus: StateFlow<TunnelStatus> = Socks5VpnService.statusFlow

    init {
        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val apps = appRepo.getInstalledApps(_uiState.value.config.allowedApps)
            _uiState.update { it.copy(installedApps = apps, isLoadingApps = false) }
        }
    }

    fun updateHost(host: String) = updateConfig { copy(host = host) }

    fun updatePort(portStr: String) {
        val port = portStr.toIntOrNull() ?: return
        updateConfig { copy(port = port) }
    }

    fun updateUsername(user: String) = updateConfig { copy(username = user) }

    fun updatePassword(pass: String) = updateConfig { copy(password = pass) }

    fun toggleUdp(enabled: Boolean) = updateConfig { copy(isUdpEnabled = enabled) }

    fun togglePerAppRouting(enabled: Boolean) = updateConfig { copy(isPerAppRoutingEnabled = enabled) }

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
        if (!config.isValid || _uiState.value.isCheckingPing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingPing = true, pingResult = null) }
            val result = Socks5Checker.checkConnection(config)
            _uiState.update { it.copy(isCheckingPing = false, pingResult = result) }
        }
    }

    fun setAppSelectorVisible(visible: Boolean) {
        _uiState.update { it.copy(isAppSelectorOpen = visible) }
    }
}
