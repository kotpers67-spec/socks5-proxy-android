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
import com.socks5proxy.app.model.TunnelState
import com.socks5proxy.app.model.TunnelStatus
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
            startVpnTunnel()
        } else {
            Toast.makeText(this, "Разрешение на VPN отклонено пользователем", Toast.LENGTH_SHORT).show()
            Socks5VpnService.updateStatus(TunnelStatus(TunnelState.DISCONNECTED, "Разрешение VPN отклонено"))
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handled */ }

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
                            Socks5VpnService.stop(this@MainActivity)
                        } else {
                            prepareAndStartVpn()
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
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPrepareLauncher.launch(vpnIntent)
        } else {
            startVpnTunnel()
        }
    }

    private fun startVpnTunnel() {
        Socks5VpnService.start(this)
    }
}
