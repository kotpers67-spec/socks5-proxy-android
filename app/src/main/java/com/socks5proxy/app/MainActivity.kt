package com.socks5proxy.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.socks5proxy.app.model.ProxyEngineMode
import com.socks5proxy.app.model.TunnelState
import com.socks5proxy.app.model.TunnelStatus
import com.socks5proxy.app.service.LocalProxyService
import com.socks5proxy.app.service.Socks5VpnService
import com.socks5proxy.app.ui.MainViewModel
import com.socks5proxy.app.ui.screens.MainScreen
import com.socks5proxy.app.ui.theme.Socks5ProxyTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            LocalProxyService.stop(this)
            Socks5VpnService.start(this)
        } else {
            Toast.makeText(this, "Разрешение на VPN отклонено пользователем", Toast.LENGTH_SHORT).show()
            Socks5VpnService.updateStatus(TunnelStatus(TunnelState.DISCONNECTED, "Разрешение VPN отклонено"))
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()

        setContent {
            Socks5ProxyTheme {
                val uiState by viewModel.uiState.collectAsState()
                val tunnelStatus by viewModel.tunnelStatus.collectAsState()

                MainScreen(
                    uiState = uiState,
                    tunnelStatus = tunnelStatus,
                    onToggleConnection = {
                        if (tunnelStatus.isRunning) {
                            LocalProxyService.stop(this@MainActivity)
                            Socks5VpnService.stop(this@MainActivity)
                        } else {
                            val active = uiState.config.activeProfile
                            if (!active.isValid) {
                                viewModel.openEditProfile(active)
                            } else {
                                if (uiState.config.engineMode == ProxyEngineMode.LOCAL_PROXY) {
                                    Socks5VpnService.stop(this@MainActivity)
                                    LocalProxyService.start(this@MainActivity)
                                } else {
                                    if (uiState.config.isPerAppRoutingEnabled && uiState.config.allowedApps.isEmpty()) {
                                        Toast.makeText(this@MainActivity, "Выберите приложения для проксирования", Toast.LENGTH_SHORT).show()
                                        viewModel.setAppSelectorVisible(true)
                                    } else {
                                        LocalProxyService.stop(this@MainActivity)
                                        prepareAndStartVpn()
                                    }
                                }
                            }
                        }
                    },
                    viewModel = viewModel
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }

    private fun prepareAndStartVpn() {
        try {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                vpnPrepareLauncher.launch(vpnIntent)
            } else {
                Socks5VpnService.start(this)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось инициализировать VPN: ${e.message}", Toast.LENGTH_SHORT).show()
            Socks5VpnService.updateStatus(TunnelStatus(TunnelState.ERROR, "Ошибка инициализации VPN: ${e.message}"))
        }
    }
}
