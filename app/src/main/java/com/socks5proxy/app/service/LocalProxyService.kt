package com.socks5proxy.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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
import com.socks5proxy.app.util.AppLogBuffer
import com.socks5proxy.app.util.BufferPool
import com.socks5proxy.app.util.ScreenStateMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

class LocalProxyService : Service() {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val totalRxBytes = AtomicLong(0L)
    private val totalTxBytes = AtomicLong(0L)
    private var screenMonitor: ScreenStateMonitor? = null

    override fun onCreate() {
        super.onCreate()
        screenMonitor = ScreenStateMonitor(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val prefs = ProxyPreferences(this)
        val config = prefs.loadConfig()

        when (action) {
            ACTION_START -> {
                val profile = config.activeProfile
                if (!profile.isValid) {
                    updateStatus(TunnelStatus(TunnelState.ERROR, "Укажите параметры прокси в настройках"))
                    stopSelf()
                    return START_NOT_STICKY
                }
                val notification = buildNotification(config)
                if (!safeStartForeground(notification)) {
                    updateStatus(TunnelStatus(TunnelState.ERROR, "Не удалось запустить фоновую службу"))
                    stopSelf()
                    return START_NOT_STICKY
                }
                startProxyServer(config)
            }
            ACTION_STOP -> {
                stopProxyServer()
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

    private fun startProxyServer(config: ProxyConfig) {
        val profile = config.activeProfile
        serverJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
        totalRxBytes.set(0L)
        totalTxBytes.set(0L)

        updateStatus(TunnelStatus(TunnelState.CONNECTING, "Запуск локального прокси на порту ${config.localListenPort}..."))
        AppLogBuffer.log("SOCKS5", "Запуск локального входа на 127.0.0.1:${config.localListenPort}")

        serverJob = serviceScope.launch {
            try {
                val probe = Socks5Checker.checkConnection(profile)
                if (!probe.isSuccess) {
                    AppLogBuffer.log("SOCKS5", "Ошибка проверки узла ${profile.host}:${profile.port}: ${probe.message}")
                    updateStatus(TunnelStatus(TunnelState.ERROR, probe.message))
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                try {
                    serverSocket?.close()
                } catch (_: Exception) {}

                val candidatePorts = linkedSetOf(
                    config.localListenPort,
                    10809,
                    10810,
                    10811,
                    20808,
                    30808,
                    0
                )

                var server: ServerSocket? = null
                var actualPort = config.localListenPort
                val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

                for (p in candidatePorts) {
                    try {
                        val s = ServerSocket()
                        s.reuseAddress = true
                        s.bind(InetSocketAddress(loopback, p), 50)
                        server = s
                        actualPort = s.localPort
                        break
                    } catch (_: Exception) {}
                }

                if (server == null) {
                    throw java.net.BindException("Все порты локального прокси заняты")
                }
                serverSocket = server

                val startTime = System.currentTimeMillis()
                val portNote = if (actualPort != config.localListenPort) " (порт ${config.localListenPort} занят)" else ""
                updateStatus(
                    TunnelStatus(
                        state = TunnelState.CONNECTED,
                        message = "SOCKS5 проверен; локальный вход 127.0.0.1:$actualPort$portNote",
                        connectedAtMillis = startTime,
                        boundPort = actualPort
                    )
                )

                safeStartForeground(buildNotification(config, actualPort))
                launch { monitorStatsLoop() }

                while (isActive && serverSocket?.isClosed == false) {
                    val clientSocket = try {
                        serverSocket?.accept() ?: break
                    } catch (_: Exception) {
                        break
                    }
                    launch(Dispatchers.IO) {
                        handleClientConnection(clientSocket, config)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Ошибка локального прокси-сервера", e)
                    updateStatus(TunnelStatus(TunnelState.ERROR, "Порт недоступен: ${e.localizedMessage ?: e.message}"))
                    stopProxyServer()
                }
            }
        }
    }

    private suspend fun handleClientConnection(clientSocket: Socket, config: ProxyConfig) {
        val profile = config.activeProfile
        var remoteSocket: Socket? = null

        try {
            clientSocket.soTimeout = 15000
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val version = clientIn.read()
            if (version != 0x05) {
                clientSocket.close()
                return
            }

            val nMethods = clientIn.read()
            if (nMethods <= 0) {
                clientSocket.close()
                return
            }
            val methods = ByteArray(nMethods)
            clientIn.readFully(methods)
            // The local listener intentionally accepts unauthenticated clients. Reject
            // clients that did not offer that method instead of claiming it was accepted.
            if (methods.none { it == 0x00.toByte() }) {
                clientOut.write(byteArrayOf(0x05, 0xFF.toByte()))
                clientOut.flush()
                clientSocket.close()
                return
            }

            clientOut.write(byteArrayOf(0x05, 0x00))
            clientOut.flush()

            val reqVer = clientIn.read()
            val reqCmd = clientIn.read()
            val reqRsv = clientIn.read()
            val reqAtyp = clientIn.read()

            if (reqVer != 0x05 || reqCmd != 0x01) {
                clientOut.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                clientOut.flush()
                clientSocket.close()
                return
            }

            val targetHost: String
            when (reqAtyp) {
                0x01 -> {
                    val ip = ByteArray(4)
                    clientIn.readFully(ip)
                    targetHost = InetAddress.getByAddress(ip).hostAddress ?: ""
                }
                0x03 -> {
                    val len = clientIn.read()
                    if (len <= 0) {
                        clientSocket.close()
                        return
                    }
                    val domainBytes = ByteArray(len)
                    clientIn.readFully(domainBytes)
                    targetHost = String(domainBytes, Charsets.UTF_8)
                }
                0x04 -> {
                    val ip = ByteArray(16)
                    clientIn.readFully(ip)
                    targetHost = InetAddress.getByAddress(ip).hostAddress ?: ""
                }
                else -> {
                    clientSocket.close()
                    return
                }
            }

            val portH = clientIn.read()
            val portL = clientIn.read()
            val targetPort = ((portH and 0xFF) shl 8) or (portL and 0xFF)
            if (portH < 0 || portL < 0 || targetPort !in 1..65535) {
                clientOut.write(byteArrayOf(0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                clientOut.flush()
                return
            }

            remoteSocket = connectThroughUpstream(profile, targetHost, targetPort, config.isFallbackDirectEnabled)

            if (remoteSocket == null) {
                clientOut.write(byteArrayOf(0x05, 0x04, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                clientOut.flush()
                clientSocket.close()
                return
            }

            clientOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            clientOut.flush()

            clientSocket.soTimeout = 0
            remoteSocket.soTimeout = 0

            val client = clientSocket
            val remote = remoteSocket

            coroutineScope {
                launch(Dispatchers.IO) {
                    try {
                        pipeData(client.getInputStream(), remote.getOutputStream(), totalTxBytes)
                    } finally {
                        runCatching { remote.shutdownOutput() }
                    }
                }
                launch(Dispatchers.IO) {
                    try {
                        pipeData(remote.getInputStream(), client.getOutputStream(), totalRxBytes)
                    } finally {
                        runCatching { client.shutdownOutput() }
                    }
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "Ошибка SOCKS5-подключения клиента: ${e.message}")
        } finally {
            runCatching { clientSocket.close() }
            runCatching { remoteSocket?.close() }
        }
    }

    private fun connectThroughUpstream(
        profile: com.socks5proxy.app.model.ProxyProfile,
        targetHost: String,
        targetPort: Int,
        allowFallback: Boolean
    ): Socket? {
        var sock: Socket? = null
        try {
            sock = Socket()
            sock.tcpNoDelay = true
            sock.soTimeout = 5000
            sock.connect(InetSocketAddress(profile.host.trim(), profile.port), 5000)

            val out = sock.getOutputStream()
            val input = sock.getInputStream()

            val hasAuth = profile.username.isNotEmpty()
            if (hasAuth) {
                out.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                out.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            out.flush()

            val ver = input.read()
            val method = input.read()
            if (ver != 0x05) throw Exception("Invalid proxy")

            if (hasAuth && method != 0x02) throw Exception("Upstream proxy rejected username/password authentication")
            if (!hasAuth && method != 0x00) throw Exception("Upstream proxy requires authentication")

            if (method == 0x02) {
                val uBytes = profile.username.toByteArray(Charsets.UTF_8)
                val pBytes = profile.password.toByteArray(Charsets.UTF_8)
                if (uBytes.size !in 1..255 || pBytes.size !in 1..255) {
                    throw Exception("SOCKS5 username/password must be 1–255 bytes")
                }
                val authReq = ByteArray(3 + uBytes.size + pBytes.size)
                authReq[0] = 0x01
                authReq[1] = uBytes.size.toByte()
                System.arraycopy(uBytes, 0, authReq, 2, uBytes.size)
                authReq[2 + uBytes.size] = pBytes.size.toByte()
                System.arraycopy(pBytes, 0, authReq, 3 + uBytes.size, pBytes.size)
                out.write(authReq)
                out.flush()

                val authVersion = input.read()
                val status = input.read()
                if (authVersion != 0x01 || status != 0x00) throw Exception("Auth failed")
            }

            val targetBytes = targetHost.toByteArray(Charsets.UTF_8)
            if (targetBytes.isEmpty() || targetBytes.size > 255) throw Exception("Invalid destination hostname")
            val connectReq = ByteArray(4 + 1 + targetBytes.size + 2)
            connectReq[0] = 0x05
            connectReq[1] = 0x01
            connectReq[2] = 0x00
            connectReq[3] = 0x03
            connectReq[4] = targetBytes.size.toByte()
            System.arraycopy(targetBytes, 0, connectReq, 5, targetBytes.size)
            connectReq[5 + targetBytes.size] = ((targetPort shr 8) and 0xFF).toByte()
            connectReq[6 + targetBytes.size] = (targetPort and 0xFF).toByte()

            out.write(connectReq)
            out.flush()

            val reply = ByteArray(4)
            input.readFully(reply)
            if (reply[0].toInt() != 0x05 || reply[1].toInt() != 0x00) throw Exception("Upstream connect failed")
            val addressLength = when (reply[3].toInt() and 0xFF) {
                0x01 -> 4
                0x04 -> 16
                0x03 -> input.read().also { if (it < 0) throw java.io.EOFException() }
                else -> throw Exception("Invalid upstream SOCKS5 reply")
            }
            input.readFully(ByteArray(addressLength + 2))
            sock.soTimeout = 0

            return sock
        } catch (e: Exception) {
            runCatching { sock?.close() }
            if (allowFallback) {
                return try {
                    val direct = Socket()
                    direct.tcpNoDelay = true
                    direct.connect(InetSocketAddress(targetHost, targetPort), 7000)
                    direct
                } catch (_: Exception) {
                    null
                }
            }
            return null
        }
    }

    private fun pipeData(input: InputStream, output: OutputStream, counter: AtomicLong) {
        BufferPool.useBuffer { buffer ->
            try {
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    output.flush()
                    counter.addAndGet(read.toLong())
                }
            } catch (_: Exception) {}
        }
    }

    private fun InputStream.readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = read(buffer, offset, buffer.size - offset)
            if (count < 0) throw java.io.EOFException("Incomplete SOCKS5 packet")
            offset += count
        }
    }

    private suspend fun monitorStatsLoop() = withContext(Dispatchers.IO) {
        var lastRx = totalRxBytes.get()
        var lastTx = totalTxBytes.get()
        var lastTimestamp = System.currentTimeMillis()

        while (currentCoroutineContext().isActive) {
            val isScreenActive = screenMonitor?.awaitScreenOnOrInterval(activeIntervalMs = 1500L, idleIntervalMs = 15000L) ?: true

            val now = System.currentTimeMillis()
            val deltaMs = (now - lastTimestamp).coerceAtLeast(1L)
            val currentRx = totalRxBytes.get()
            val currentTx = totalTxBytes.get()

            val rxSpeed = if (isScreenActive && currentRx >= lastRx) {
                ((currentRx - lastRx) * 1000L / deltaMs).coerceAtLeast(0L)
            } else 0L

            val txSpeed = if (isScreenActive && currentTx >= lastTx) {
                ((currentTx - lastTx) * 1000L / deltaMs).coerceAtLeast(0L)
            } else 0L

            lastRx = currentRx
            lastTx = currentTx
            lastTimestamp = now

            _statusFlow.update { current ->
                if (current.state == TunnelState.CONNECTED) {
                    current.copy(
                        bytesIn = currentRx,
                        bytesOut = currentTx,
                        rxSpeedBps = rxSpeed,
                        txSpeedBps = txSpeed
                    )
                } else current
            }
        }
    }

    private fun buildNotification(config: ProxyConfig, port: Int = config.localListenPort): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, LocalProxyService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingDisconnect = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Socks5App.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_proxy)
            .setContentTitle("SOCKS5 — локальный прокси")
            .setContentText("127.0.0.1:$port • только настроенные приложения")
            .setSubText("Системный VPN не меняется")
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(R.drawable.ic_stat_proxy, getString(R.string.disconnect_action), pendingDisconnect)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopProxyServer() {
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        serviceJob.cancelChildren()
        updateStatus(TunnelStatus(TunnelState.DISCONNECTED, "Прокси отключен"))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopProxyServer()
        screenMonitor?.destroy()
        screenMonitor = null
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocalProxyService"
        const val ACTION_START = "com.socks5proxy.app.ACTION_LOCAL_START"
        const val ACTION_STOP = "com.socks5proxy.app.ACTION_LOCAL_STOP"

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
                val intent = Intent(context, LocalProxyService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось запустить LocalProxyService", e)
                updateStatus(TunnelStatus(TunnelState.ERROR, "Ошибка запуска службы: ${e.message}"))
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, LocalProxyService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось остановить LocalProxyService", e)
            }
        }
    }
}
