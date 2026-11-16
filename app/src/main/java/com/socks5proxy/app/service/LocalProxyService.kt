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
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val totalRxBytes = AtomicLong(0L)
    private val totalTxBytes = AtomicLong(0L)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val prefs = ProxyPreferences(this)
        val config = prefs.loadConfig()

        when (action) {
            ACTION_START -> {
                safeStartForeground(buildNotification(config))
                startProxyServer(config)
            }
            ACTION_STOP -> {
                stopProxyServer()
            }
        }

        return START_NOT_STICKY
    }

    private fun safeStartForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
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

    private fun startProxyServer(config: ProxyConfig) {
        val profile = config.activeProfile
        if (!profile.isValid) {
            updateStatus(TunnelStatus(TunnelState.ERROR, "Укажите параметры прокси в настройках"))
            return
        }

        updateStatus(TunnelStatus(TunnelState.CONNECTING, "Запуск локального прокси на порту ${config.localListenPort}..."))

        serviceScope.launch {
            try {
                try {
                    serverSocket?.close()
                } catch (_: Exception) {}

                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), config.localListenPort), 50)
                serverSocket = server

                val startTime = System.currentTimeMillis()
                updateStatus(
                    TunnelStatus(
                        state = TunnelState.CONNECTED,
                        message = "127.0.0.1:${config.localListenPort} -> ${profile.host}:${profile.port}",
                        connectedAtMillis = startTime
                    )
                )

                safeStartForeground(buildNotification(config))

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
                    updateStatus(TunnelStatus(TunnelState.ERROR, "Не удалось открыть порт ${config.localListenPort}: ${e.localizedMessage ?: e.message}"))
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
            val methods = ByteArray(nMethods)
            clientIn.read(methods)

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
                    clientIn.read(ip)
                    targetHost = InetAddress.getByAddress(ip).hostAddress ?: ""
                }
                0x03 -> {
                    val len = clientIn.read()
                    val domainBytes = ByteArray(len)
                    clientIn.read(domainBytes)
                    targetHost = String(domainBytes, Charsets.UTF_8)
                }
                0x04 -> {
                    val ip = ByteArray(16)
                    clientIn.read(ip)
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

            coroutineScope {
                launch(Dispatchers.IO) {
                    pipeData(clientSocket.getInputStream(), remoteSocket.getOutputStream(), totalRxBytes)
                }
                launch(Dispatchers.IO) {
                    pipeData(remoteSocket.getInputStream(), clientSocket.getOutputStream(), totalTxBytes)
                }
            }

        } catch (_: Exception) {
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
        try {
            val sock = Socket()
            sock.tcpNoDelay = true
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

            if (method == 0x02) {
                val uBytes = profile.username.toByteArray(Charsets.UTF_8)
                val pBytes = profile.password.toByteArray(Charsets.UTF_8)
                val authReq = ByteArray(3 + uBytes.size + pBytes.size)
                authReq[0] = 0x01
                authReq[1] = uBytes.size.toByte()
                System.arraycopy(uBytes, 0, authReq, 2, uBytes.size)
                authReq[2 + uBytes.size] = pBytes.size.toByte()
                System.arraycopy(pBytes, 0, authReq, 3 + uBytes.size, pBytes.size)
                out.write(authReq)
                out.flush()

                input.read()
                val status = input.read()
                if (status != 0x00) throw Exception("Auth failed")
            }

            val targetBytes = targetHost.toByteArray(Charsets.UTF_8)
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

            val repVer = input.read()
            val repStatus = input.read()
            if (repVer != 0x05 || repStatus != 0x00) throw Exception("Upstream connect failed")

            input.read()
            val atyp = input.read()
            when (atyp) {
                0x01 -> input.skip(4 + 2)
                0x03 -> {
                    val l = input.read()
                    input.skip((l + 2).toLong())
                }
                0x04 -> input.skip(16 + 2)
            }

            return sock
        } catch (e: Exception) {
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
        val buffer = ByteArray(8192)
        try {
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
                counter.addAndGet(read.toLong())
            }
        } catch (_: Exception) {}
    }

    private suspend fun monitorStatsLoop() = withContext(Dispatchers.IO) {
        while (currentCoroutineContext().isActive) {
            delay(1500)
            val rx = totalRxBytes.get()
            val tx = totalTxBytes.get()
            _statusFlow.update { current ->
                if (current.state == TunnelState.CONNECTED && (current.bytesIn != rx || current.bytesOut != tx)) {
                    current.copy(bytesIn = rx, bytesOut = tx)
                } else current
            }
        }
    }

    private fun buildNotification(config: ProxyConfig): Notification {
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
            .setContentTitle("SOCKS5 Прокси активен (Без VPN)")
            .setContentText("127.0.0.1:${config.localListenPort} • Трафик идет через текущий VPN")
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(R.drawable.ic_stat_proxy, getString(R.string.disconnect_action), pendingDisconnect)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopProxyServer() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        serviceJob.cancel()
        updateStatus(TunnelStatus(TunnelState.DISCONNECTED, "Прокси отключен"))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopProxyServer()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocalProxyService"
        const val ACTION_START = "com.socks5proxy.app.ACTION_LOCAL_START"
        const val ACTION_STOP = "com.socks5proxy.app.ACTION_LOCAL_STOP"

        private val _statusFlow = MutableStateFlow(TunnelStatus())
        val statusFlow: StateFlow<TunnelStatus> = _statusFlow.asStateFlow()

        fun updateStatus(status: TunnelStatus) {
            _statusFlow.value = status
        }

        fun start(context: Context) {
            val intent = Intent(context, LocalProxyService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocalProxyService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
