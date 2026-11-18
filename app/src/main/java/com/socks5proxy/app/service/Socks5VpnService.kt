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
import com.socks5proxy.app.network.Socks5Checker
import com.socks5proxy.app.util.ScreenStateMonitor
import libXray.DialerController
import libXray.LibXray
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.amnezia.awg.hevtunnel.TProxyService
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class Socks5VpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var configFile: File? = null
    private var statsJob: Job? = null
    private var isHevRunning = false
    private var isXrayRunning = false
    private var isXrayDnsConfigured = false
    private var xrayDialerController: DialerController? = null
    private var xrayMetricsPort = 0
    private var xrayStatsOutboundTag = VpnTunnelConfigBuilder.VPN_OUTBOUND_TAG
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var screenMonitor: ScreenStateMonitor? = null

    override fun onCreate() {
        super.onCreate()
        screenMonitor = ScreenStateMonitor(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_CONNECT
        val prefs = ProxyPreferences(this)
        val config = prefs.loadConfig()

        when (action) {
            ACTION_CONNECT -> {
                val profile = config.activeProfile
                val needsSocks = config.engineMode != com.socks5proxy.app.model.ProxyEngineMode.VPN_PROFILE ||
                    config.isVpnToSocksChainEnabled
                if (needsSocks && !profile.isValid) {
                    updateStatus(TunnelStatus(TunnelState.ERROR, "Некорректные параметры SOCKS5"))
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (config.engineMode == com.socks5proxy.app.model.ProxyEngineMode.VPN_PROFILE && config.activeVpnProfile == null) {
                    updateStatus(TunnelStatus(TunnelState.ERROR, "Импортируйте и выберите VPN-сервер"))
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (config.isPerAppRoutingEnabled && config.allowedApps.isEmpty()) {
                    updateStatus(TunnelStatus(TunnelState.ERROR, "Выберите приложения для проксирования"))
                    stopSelf()
                    return START_NOT_STICKY
                }
                val notification = buildNotification(config)
                if (!safeStartForeground(notification)) {
                    updateStatus(TunnelStatus(TunnelState.ERROR, "Не удалось запустить фоновую службу VPN"))
                    stopSelf()
                    return START_NOT_STICKY
                }
                startTunnel(config)
            }
            ACTION_DISCONNECT -> {
                stopTunnel()
            }
        }

        return START_NOT_STICKY
    }

    private fun safeStartForeground(notification: Notification): Boolean {
        return try {
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
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка safeStartForeground: ${e.message}")
            try {
                startForeground(Socks5App.NOTIFICATION_ID, notification)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun startTunnel(config: ProxyConfig) {
        updateStatus(TunnelStatus(TunnelState.CONNECTING, "Установка соединения..."))

        serviceScope.launch {
            try {
            cleanup()
            if (config.engineMode == com.socks5proxy.app.model.ProxyEngineMode.VPN_PROFILE) {
                startXrayTunnel(config)
                return@launch
            }
            val profile = config.activeProfile
            val probe = Socks5Checker.checkConnection(profile)
            if (!probe.isSuccess) {
                updateStatus(TunnelStatus(TunnelState.ERROR, probe.message))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            configFile = createHevConfigFile(config)

            val builder = Builder()
                .setSession("SOCKS5: ${profile.host}:${profile.port}")
                .setMtu(config.mtu)
                .addAddress("198.18.0.1", 24)
                .addAddress("fd00::1", 126)
                .addDnsServer(config.primaryDns)
                .addDnsServer(config.secondaryDns)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)

            if (config.isPerAppRoutingEnabled) {
                val safeApps = config.allowedApps.filter { it != packageName }
                if (safeApps.isEmpty()) {
                    throw IllegalStateException("Выберите хотя бы одно установленное приложение")
                }
                var routedApps = 0
                for (pkg in safeApps) {
                    try {
                        builder.addAllowedApplication(pkg)
                        routedApps++
                    } catch (e: Exception) {
                        Log.w(TAG, "Пропуск пакета $pkg: ${e.message}")
                    }
                }
                if (routedApps == 0) {
                    throw IllegalStateException("Ни одно выбранное приложение не установлено или недоступно")
                }
            } else {
                builder.addDisallowedApplication(packageName)
            }

            val pfd = builder.establish() ?: throw IllegalStateException("Не удалось создать сетевой интерфейс VpnService")
            vpnInterface = pfd

            safeStartForeground(buildNotification(config))
            TProxyService.TProxyStartService(configFile!!.absolutePath, pfd.fd)
            isHevRunning = true

            val startTime = System.currentTimeMillis()
            updateStatus(
                TunnelStatus(
                    state = TunnelState.CONNECTED,
                    message = "Подключено к ${profile.host}:${profile.port}",
                    connectedAtMillis = startTime
                )
            )

            startStatsPolling()

            } catch (e: LinkageError) {
                val engine = if (config.engineMode == com.socks5proxy.app.model.ProxyEngineMode.VPN_PROFILE) "Xray" else "SOCKS5"
                Log.e(TAG, "Не удалось загрузить ядро $engine", e)
                updateStatus(TunnelStatus(TunnelState.ERROR, "Не удалось загрузить ядро $engine: ${e.localizedMessage ?: e.message}"))
                cleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска SOCKS5 туннеля", e)
            updateStatus(TunnelStatus(TunnelState.ERROR, "Ошибка: ${e.localizedMessage ?: e.message}"))
            cleanup()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            }
        }
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            var lastRx = 0L
            var lastTx = 0L
            var lastTimestamp = System.currentTimeMillis()
            var hasBaseline = false

            while (isActive) {
                val isScreenActive = screenMonitor?.awaitScreenOnOrInterval(activeIntervalMs = 1500L, idleIntervalMs = 15000L) ?: true
                if (!isActive) break

                try {
                    val values = if (isXrayRunning) {
                        readXrayTrafficStats()
                    } else {
                        val stats = TProxyService.TProxyGetStats()
                        if (stats != null && stats.size >= 4) stats[3].coerceAtLeast(0L) to stats[1].coerceAtLeast(0L) else null
                    }
                    if (values != null) {
                        val now = System.currentTimeMillis()
                        val deltaMs = (now - lastTimestamp).coerceAtLeast(1L)
                        val (rx, tx) = values

                        val rxSpeed = if (hasBaseline && isScreenActive && rx >= lastRx) {
                            ((rx - lastRx) * 1000L / deltaMs).coerceAtLeast(0L)
                        } else 0L

                        val txSpeed = if (hasBaseline && isScreenActive && tx >= lastTx) {
                            ((tx - lastTx) * 1000L / deltaMs).coerceAtLeast(0L)
                        } else 0L

                        lastRx = rx
                        lastTx = tx
                        lastTimestamp = now
                        hasBaseline = true

                        _statusFlow.update { current ->
                            if (current.state == TunnelState.CONNECTED) {
                                current.copy(
                                    bytesIn = rx,
                                    bytesOut = tx,
                                    rxSpeedBps = rxSpeed,
                                    txSpeedBps = txSpeed
                                )
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

    private fun startXrayTunnel(config: ProxyConfig) {
        val server = config.activeVpnProfile ?: throw IllegalStateException("Импортируйте VPN-сервер")
        if (config.isVpnToSocksChainEnabled && !config.activeProfile.isValid) {
            throw IllegalStateException("Проверьте параметры SOCKS5 для цепочки VPN → SOCKS5")
        }

        val builder = Builder()
            .setSession("VPN: ${server.name}")
            .setMtu(config.mtu)
            .addAddress("198.18.0.1", 24)
            .addAddress("fd00::1", 126)
            .addDnsServer(config.primaryDns)
            .addDnsServer(config.secondaryDns)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)

        if (config.isPerAppRoutingEnabled) {
            val selectedApps = config.allowedApps.filter { it != packageName }
            if (selectedApps.isEmpty()) throw IllegalStateException("Выберите хотя бы одно приложение")
            var routedApps = 0
            selectedApps.forEach { packageName ->
                try {
                    builder.addAllowedApplication(packageName)
                    routedApps++
                } catch (e: Exception) {
                    Log.w(TAG, "Пропуск приложения $packageName: ${e.message}")
                }
            }
            if (routedApps == 0) throw IllegalStateException("Выбранные приложения недоступны")
        } else {
            builder.addDisallowedApplication(packageName)
        }

        val tun = builder.establish() ?: throw IllegalStateException("Android не создал VPN-интерфейс")
        vpnInterface = tun
        safeStartForeground(buildNotification(config))

        val metricsPort = findFreeLocalPort()
        xrayMetricsPort = metricsPort
        val built = VpnTunnelConfigBuilder.build(config, server, tun.fd, metricsPort)
        xrayStatsOutboundTag = built.measuredOutboundTag

        val controller = object : DialerController {
            override fun protectFd(fd: Long): Boolean = protect(fd.toInt())
        }
        xrayDialerController = controller
        LibXray.registerDialerController(controller)
        LibXray.setDNS(controller, "${config.primaryDns}:53")
        isXrayDnsConfigured = true

        invokeXray("runXray", JSONObject().put("xrayJson", built.json))
        isXrayRunning = true

        val chainLabel = if (config.isVpnToSocksChainEnabled) " → SOCKS5 ${config.activeProfile.host}:${config.activeProfile.port}" else ""
        updateStatus(
            TunnelStatus(
                state = TunnelState.CONNECTED,
                message = "VPN ${server.name}$chainLabel",
                connectedAtMillis = System.currentTimeMillis()
            )
        )
        startStatsPolling()
    }

    private fun invokeXray(method: String, payload: JSONObject): JSONObject {
        val request = JSONObject()
            .put("apiVersion", 3)
            .put("method", method)
            .put("payload", payload)
        val response = JSONObject(LibXray.invoke(request.toString()))
        if (!response.optBoolean("success")) {
            throw IllegalStateException(response.optString("error").ifBlank { "Xray не запустился" })
        }
        return response
    }

    private fun findFreeLocalPort(): Int = java.net.ServerSocket(0).use { it.localPort }

    private fun readXrayTrafficStats(): Pair<Long, Long>? {
        val connection = URL("http://127.0.0.1:$xrayMetricsPort/debug/vars").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 1000
            connection.readTimeout = 1000
            val stats = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .optJSONObject("stats") ?: return 0L to 0L
            val prefix = "outbound>>>$xrayStatsOutboundTag>>>traffic>>>"
            stats.optLong("${prefix}downlink") to stats.optLong("${prefix}uplink")
        } finally {
            connection.disconnect()
        }
    }

    private fun createHevConfigFile(config: ProxyConfig): File {
        val file = File(cacheDir, "hev_tunnel_config.yml")
        val profile = config.activeProfile

        val cleanUser = profile.username.replace("\r", "").replace("\n", "").replace("'", "''")
        val cleanPass = profile.password.replace("\r", "").replace("\n", "").replace("'", "''")
        val cleanHost = profile.host.trim().replace("\r", "").replace("\n", "").replace("'", "''")

        // Keep these lines indented under the `socks5:` YAML mapping.
        val authYaml = if (cleanUser.isNotBlank()) {
            "  username: '$cleanUser'\n  password: '$cleanPass'"
        } else ""

        val udpMode = if (profile.isUdpEnabled) "udp" else "none"

        val yamlContent = """
tunnel:
  name: tun0
  mtu: ${config.mtu}
  ipv4: 198.18.0.1
  ipv6: 'fd00::1'
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
        val vpnProfile = config.activeVpnProfile
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

        val title = if (config.engineMode == com.socks5proxy.app.model.ProxyEngineMode.VPN_PROFILE) {
            "VPN профиль активен"
        } else {
            "SOCKS5 прокси активен"
        }
        val route = if (config.engineMode == com.socks5proxy.app.model.ProxyEngineMode.VPN_PROFILE) {
            val chain = if (config.isVpnToSocksChainEnabled) " → SOCKS5 ${profile.host}:${profile.port}" else ""
            "${vpnProfile?.name ?: "VPN"}$chain • $subtext"
        } else {
            "${profile.host}:${profile.port} • $subtext"
        }

        return NotificationCompat.Builder(this, Socks5App.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_proxy)
            .setContentTitle(title)
            .setContentText(route)
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

        if (isHevRunning) {
            isHevRunning = false
            try {
                TProxyService.TProxyStopService()
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка остановки hev-socks5: ${e.message}")
            }
        }

        if (isXrayRunning) {
            isXrayRunning = false
            try {
                invokeXray("stopXray", JSONObject())
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка остановки Xray: ${e.message}")
            }
        }
        if (isXrayDnsConfigured) {
            isXrayDnsConfigured = false
            try {
                LibXray.resetDNS()
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка сброса DNS Xray: ${e.message}")
            }
        }
        xrayDialerController = null
        xrayMetricsPort = 0

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
        screenMonitor?.destroy()
        screenMonitor = null
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        cleanup()
        updateStatus(TunnelStatus(TunnelState.DISCONNECTED, "VPN соединение отозвано системой"))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onRevoke()
    }

    companion object {
        private const val TAG = "Socks5VpnService"
        const val ACTION_CONNECT = "com.socks5proxy.app.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.socks5proxy.app.ACTION_DISCONNECT"

        private val _statusFlow = MutableStateFlow(TunnelStatus())
        val statusFlow: StateFlow<TunnelStatus> = _statusFlow.asStateFlow()

        fun updateStatus(status: TunnelStatus) {
            val previous = _statusFlow.value
            _statusFlow.value = status
            if (previous.state != status.state || previous.boundPort != status.boundPort) {
                ProxyQuickTileService.requestRefresh(Socks5App.appContext)
            }
        }

        fun start(context: Context) {
            try {
                val intent = Intent(context, Socks5VpnService::class.java).apply {
                    action = ACTION_CONNECT
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось запустить Socks5VpnService", e)
                updateStatus(TunnelStatus(TunnelState.ERROR, "Ошибка запуска службы: ${e.message}"))
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, Socks5VpnService::class.java).apply {
                    action = ACTION_DISCONNECT
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось остановить Socks5VpnService", e)
            }
        }
    }
}
