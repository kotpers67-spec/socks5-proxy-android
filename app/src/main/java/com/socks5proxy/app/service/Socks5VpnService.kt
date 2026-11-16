package com.socks5proxy.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.socks5proxy.app.MainActivity
import com.socks5proxy.app.R
import com.socks5proxy.app.Socks5App
import com.socks5proxy.app.data.ProxyPreferences
import com.socks5proxy.app.model.ProxyConfig
import com.socks5proxy.app.model.TunnelState
import com.socks5proxy.app.model.TunnelStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.amnezia.awg.hevtunnel.TProxyService
import java.io.File
import java.io.FileOutputStream

class Socks5VpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var configFile: File? = null
    private var statsJob: Job? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_CONNECT
        val prefs = ProxyPreferences(this)
        val config = prefs.loadConfig()

        when (action) {
            ACTION_CONNECT -> {
                safeStartForeground(buildNotification(config))
                startTunnel(config)
            }
            ACTION_DISCONNECT -> {
                stopTunnel()
            }
        }

        return START_NOT_STICKY
    }

    private fun safeStartForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
                ServiceCompat.startForeground(this, Socks5App.NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(Socks5App.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка safeStartForeground: ${e.message}")
            try {
                startForeground(Socks5App.NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    private fun startTunnel(config: ProxyConfig) {
        val profile = config.activeProfile
        if (!profile.isValid) {
            updateStatus(TunnelStatus(TunnelState.ERROR, "Некорректные параметры SOCKS5"))
            return
        }

        if (config.isPerAppRoutingEnabled && config.allowedApps.isEmpty()) {
            updateStatus(TunnelStatus(TunnelState.ERROR, "Выберите хотя бы одно приложение для проксирования"))
            return
        }

        updateStatus(TunnelStatus(TunnelState.CONNECTING, "Установка соединения..."))

        try {
            configFile = createHevConfigFile(config)

            val builder = Builder()
                .setSession("SOCKS5: ${profile.host}:${profile.port}")
                .setMtu(config.mtu)
                .addAddress("198.18.0.1", 24)
                .addDnsServer(config.primaryDns)
                .addDnsServer(config.secondaryDns)
                .addRoute("0.0.0.0", 0)

            if (config.isPerAppRoutingEnabled) {
                var addedCount = 0
                val safeApps = config.allowedApps.filter { it != packageName }
                for (pkg in safeApps) {
                    try {
                        builder.addAllowedApplication(pkg)
                        addedCount++
                    } catch (e: Exception) {
                        Log.w(TAG, "Пропуск пакета $pkg: ${e.message}")
                    }
                }
            } else {
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Не удалось исключить собственный пакет: ${e.message}")
                }
            }

            val pfd = builder.establish() ?: throw IllegalStateException("Не удалось создать сетевой интерфейс VpnService")
            vpnInterface = pfd

            safeStartForeground(buildNotification(config))
            TProxyService.TProxyStartService(configFile!!.absolutePath, pfd.fd)

            val startTime = System.currentTimeMillis()
            updateStatus(
                TunnelStatus(
                    state = TunnelState.CONNECTED,
                    message = "Подключено к ${profile.host}:${profile.port}",
                    connectedAtMillis = startTime
                )
            )

            startStatsPolling()

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска SOCKS5 туннеля", e)
            updateStatus(TunnelStatus(TunnelState.ERROR, "Ошибка: ${e.localizedMessage ?: e.message}"))
            cleanup()
            stopSelf()
        }
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            while (isActive) {
                delay(1500)
                try {
                    val stats = TProxyService.TProxyGetStats()
                    if (stats != null && stats.size >= 2) {
                        val rx = stats[0]
                        val tx = stats[1]
                        _statusFlow.update { current ->
                            if (current.state == TunnelState.CONNECTED && (current.bytesIn != rx || current.bytesOut != tx)) {
                                current.copy(bytesIn = rx, bytesOut = tx)
                            } else current
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Ошибка получения статистики: ${e.message}")
                }
            }
        }
    }

    private fun createHevConfigFile(config: ProxyConfig): File {
        val file = File(cacheDir, "hev_tunnel_config.yml")
        val profile = config.activeProfile

        val cleanUser = profile.username.replace("\r", "").replace("\n", "").replace("'", "''")
        val cleanPass = profile.password.replace("\r", "").replace("\n", "").replace("'", "''")
        val cleanHost = profile.host.trim().replace("\r", "").replace("\n", "").replace("'", "''")

        val authYaml = if (cleanUser.isNotBlank()) {
            """
  username: '$cleanUser'
  password: '$cleanPass'
            """.trimIndent()
        } else ""

        val udpMode = if (profile.isUdpEnabled) "udp" else "none"

        val yamlContent = """
tunnel:
  name: tun0
  mtu: ${config.mtu}
  ipv4: 198.18.0.1
socks5:
  port: ${profile.port}
  address: '$cleanHost'
  udp: '$udpMode'
$authYaml

misc:
  connect-timeout: 5000
  log-level: warn
""".trimIndent()

        FileOutputStream(file).use { out ->
            out.write(yamlContent.toByteArray(Charsets.UTF_8))
        }

        return file
    }

    private fun buildNotification(config: ProxyConfig): Notification {
        val profile = config.activeProfile
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, Socks5VpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingDisconnect = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtext = if (config.isPerAppRoutingEnabled && config.allowedApps.isNotEmpty()) {
            "Выборочный режим (${config.allowedApps.size} прил.)"
        } else {
            "Все приложения"
        }

        return NotificationCompat.Builder(this, Socks5App.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_proxy)
            .setContentTitle("SOCKS5 прокси активен")
            .setContentText("${profile.host}:${profile.port} • $subtext")
            .setSubText(subtext)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(R.drawable.ic_stat_proxy, getString(R.string.disconnect_action), pendingDisconnect)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopTunnel() {
        cleanup()
        updateStatus(TunnelStatus(TunnelState.DISCONNECTED, "Туннель отключен"))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        statsJob?.cancel()
        statsJob = null

        try {
            TProxyService.TProxyStopService()
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка остановки hev-socks5: ${e.message}")
        }

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка закрытия VPN интерфейса: ${e.message}")
        }
        vpnInterface = null

        configFile?.let { secureWipeAndDelete(it) }
        configFile = null
    }

    private fun secureWipeAndDelete(file: File) {
        try {
            if (file.exists()) {
                val len = file.length().toInt()
                if (len > 0) {
                    FileOutputStream(file).use { out ->
                        out.write(ByteArray(len))
                        out.flush()
                    }
                }
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка удаления: ${e.message}")
        }
    }

    override fun onDestroy() {
        cleanup()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        cleanup()
        updateStatus(TunnelStatus(TunnelState.DISCONNECTED, "VPN соединение отозвано системой"))
        super.onRevoke()
    }

    companion object {
        private const val TAG = "Socks5VpnService"
        const val ACTION_CONNECT = "com.socks5proxy.app.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.socks5proxy.app.ACTION_DISCONNECT"

        private val _statusFlow = MutableStateFlow(TunnelStatus())
        val statusFlow: StateFlow<TunnelStatus> = _statusFlow.asStateFlow()

        fun updateStatus(status: TunnelStatus) {
            _statusFlow.value = status
        }

        fun start(context: Context) {
            val intent = Intent(context, Socks5VpnService::class.java).apply {
                action = ACTION_CONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, Socks5VpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }
}
