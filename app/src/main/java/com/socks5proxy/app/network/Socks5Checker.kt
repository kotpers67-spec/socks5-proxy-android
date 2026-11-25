package com.socks5proxy.app.network

import com.socks5proxy.app.model.ProxyProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class PingResult(
    val isSuccess: Boolean,
    val latencyMs: Long = 0L,
    val message: String
)

data class PublicIpResult(
    val isSuccess: Boolean,
    val ip: String = "",
    val country: String = "",
    val message: String = ""
)

object Socks5Checker {
    private const val SOCKS_VERSION_5 = 0x05
    private const val METHOD_NO_AUTH = 0x00
    private const val METHOD_USER_PASS = 0x02
    private const val METHOD_NO_ACCEPTABLE = 0xFF
    private const val AUTH_SUBNEGOTIATION_VER = 0x01
    private const val AUTH_STATUS_SUCCESS = 0x00

    suspend fun pingTcp(host: String, port: Int, timeoutMs: Int = 3000): PingResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty() || port !in 1..65535) {
            return@withContext PingResult(false, 0, "Некорректный адрес или порт")
        }

        // 1. Резолвим DNS ДО запуска таймера замера задержки.
        // Это исключает скачки пинга из-за задержек мобильного DNS-сервера (джиттер 100-800 мс).
        val inetAddress = try {
            withTimeout(timeoutMs.toLong()) {
                InetAddress.getByName(cleanHost)
            }
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            return@withContext PingResult(false, 0, "Таймаут DNS")
        } catch (_: java.net.UnknownHostException) {
            return@withContext PingResult(false, 0, "DNS ошибка")
        } catch (e: Exception) {
            return@withContext PingResult(false, 0, "Сбой DNS: ${e.message ?: "неизвестно"}")
        }

        val socketAddress = InetSocketAddress(inetAddress, port)
        var minLatency = Long.MAX_VALUE
        var successCount = 0
        var lastError: String? = null

        // 2. Серия из 3 замеров TCP handshake (SYN -> SYN/ACK) с выбором минимального RTT.
        // Первая проба прогревает сокетный стек и будит радиомодем сотовой связи (RRC transition),
        // последующие пробы дают истинное и стабильное значение задержки физического канала.
        val attempts = 3
        val singleTimeout = timeoutMs.coerceAtLeast(1000)

        for (i in 0 until attempts) {
            var socket: Socket? = null
            try {
                socket = Socket().apply {
                    tcpNoDelay = true
                    soTimeout = singleTimeout
                }
                val startNano = System.nanoTime()
                socket.connect(socketAddress, singleTimeout)
                val latencyMs = (System.nanoTime() - startNano) / 1_000_000L
                if (latencyMs < minLatency) {
                    minLatency = latencyMs
                }
                successCount++
            } catch (e: Exception) {
                lastError = when (e) {
                    is java.net.SocketTimeoutException -> "Таймаут"
                    is java.net.ConnectException -> "Недоступен"
                    else -> e.message ?: "Сбой"
                }
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }
        }

        if (successCount > 0) {
            PingResult(true, minLatency, "$minLatency мс")
        } else {
            PingResult(false, 0, lastError ?: "Недоступен")
        }
    }

    suspend fun checkConnection(profile: ProxyProfile, timeoutMs: Int = 4000): PingResult = withContext(Dispatchers.IO) {
        var socket: Socket? = null

        try {
            val inetAddress = withTimeout(timeoutMs.toLong()) {
                InetAddress.getByName(profile.host.trim())
            }

            socket = Socket().apply {
                tcpNoDelay = true
                sendBufferSize = 1024
                receiveBufferSize = 1024
                soTimeout = timeoutMs
            }

            val socketAddress = InetSocketAddress(inetAddress, profile.port)
            socket.connect(socketAddress, timeoutMs)

            val out: OutputStream = socket.getOutputStream()
            val input: InputStream = socket.getInputStream()
            val hasAuth = profile.username.isNotEmpty()

            if (hasAuth) {
                out.write(byteArrayOf(SOCKS_VERSION_5.toByte(), 0x02, METHOD_NO_AUTH.toByte(), METHOD_USER_PASS.toByte()))
            } else {
                out.write(byteArrayOf(SOCKS_VERSION_5.toByte(), 0x01, METHOD_NO_AUTH.toByte()))
            }
            out.flush()

            val ver = input.read()
            if (ver == -1) {
                return@withContext PingResult(false, 0, "Сервер разорвал соединение (EOF)")
            }
            if (ver != SOCKS_VERSION_5) {
                return@withContext PingResult(false, 0, "Сервер не поддерживает SOCKS5 (версия: $ver)")
            }

            val chosenMethod = input.read()
            if (chosenMethod == -1) {
                return@withContext PingResult(false, 0, "Сервер разорвал соединение при согласовании метода")
            }
            if (chosenMethod == METHOD_NO_ACCEPTABLE) {
                return@withContext PingResult(false, 0, "Сервер отклонил предложенные методы авторизации")
            }
            if (chosenMethod != METHOD_NO_AUTH && chosenMethod != METHOD_USER_PASS) {
                return@withContext PingResult(false, 0, "Сервер выбрал неподдерживаемый метод авторизации ($chosenMethod)")
            }

            if (chosenMethod == METHOD_USER_PASS) {
                if (!hasAuth) {
                    return@withContext PingResult(false, 0, "Сервер требует авторизацию (логин и пароль)")
                }

                val uBytes = profile.username.toByteArray(Charsets.UTF_8)
                val pBytes = profile.password.toByteArray(Charsets.UTF_8)

                if (uBytes.size !in 1..255 || pBytes.size !in 1..255) {
                    return@withContext PingResult(false, 0, "Длина логина или пароля превышает лимит RFC 1929 (255 байт)")
                }

                val authReq = ByteArray(3 + uBytes.size + pBytes.size).apply {
                    this[0] = AUTH_SUBNEGOTIATION_VER.toByte()
                    this[1] = uBytes.size.toByte()
                    System.arraycopy(uBytes, 0, this, 2, uBytes.size)
                    this[2 + uBytes.size] = pBytes.size.toByte()
                    System.arraycopy(pBytes, 0, this, 3 + uBytes.size, pBytes.size)
                }

                out.write(authReq)
                out.flush()

                val authVer = input.read()
                val authStatus = input.read()

                if (authVer == -1 || authStatus == -1) {
                    return@withContext PingResult(false, 0, "Сервер разорвал соединение в процессе аутентификации")
                }
                if (authVer != AUTH_SUBNEGOTIATION_VER || authStatus != AUTH_STATUS_SUCCESS) {
                    return@withContext PingResult(false, 0, "Неверный логин или пароль SOCKS5")
                }
            }

            val probeHost = "cp.cloudflare.com"
            val target = probeHost.toByteArray(Charsets.US_ASCII)
            out.write(byteArrayOf(
                SOCKS_VERSION_5.toByte(), 0x01, 0x00, 0x03,
                target.size.toByte()
            ))
            out.write(target)
            out.write(byteArrayOf(0x00, 0x50)) // порт 80
            out.flush()

            val reply = ByteArray(4)
            input.readFully(reply)
            if ((reply[0].toInt() and 0xFF) != SOCKS_VERSION_5) {
                return@withContext PingResult(false, 0, "Некорректный ответ SOCKS5 на CONNECT")
            }
            val replyCode = reply[1].toInt() and 0xFF
            if (replyCode != 0) {
                return@withContext PingResult(false, 0, "Прокси отклонил тестовое соединение (код $replyCode)")
            }
            val boundAddressLength = when (reply[3].toInt() and 0xFF) {
                0x01 -> 4
                0x04 -> 16
                0x03 -> input.read().also { if (it < 0) throw java.io.EOFException() }
                else -> return@withContext PingResult(false, 0, "Некорректный адрес в ответе SOCKS5")
            }
            input.readFully(ByteArray(boundAddressLength + 2))

            val httpStartNano = System.nanoTime()
            out.write(
                "GET /generate_204 HTTP/1.1\r\nHost: $probeHost\r\nUser-Agent: SOCKS5Proxy/1.5\r\nConnection: close\r\n\r\n"
                    .toByteArray(Charsets.US_ASCII)
            )
            out.flush()
            val httpStatusLine = input.readHttpLine(256)
            if (!httpStatusLine.startsWith("HTTP/1.")) {
                return@withContext PingResult(false, 0, "Прокси подключился, но ответ не получен ($httpStatusLine)")
            }

            // Замеряем чистую сквозную задержку HTTP-запроса через прокси (Real Delay)
            val latencyMs = (System.nanoTime() - httpStartNano) / 1_000_000L
            PingResult(true, latencyMs, "$latencyMs мс")

        } catch (e: CancellationException) {
            throw e
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            PingResult(false, 0, "Таймаут разрешения DNS или подключения (${timeoutMs} мс)")
        } catch (e: java.net.SocketTimeoutException) {
            PingResult(false, 0, "Таймаут сокета сервера (${timeoutMs} мс)")
        } catch (e: java.net.ConnectException) {
            PingResult(false, 0, "Сервер недоступен или целевой порт закрыт")
        } catch (e: java.net.UnknownHostException) {
            PingResult(false, 0, "Не удалось разрешить адрес узла (ошибка DNS)")
        } catch (e: Exception) {
            PingResult(false, 0, "Ошибка SOCKS5: ${e.localizedMessage ?: e.message}")
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    suspend fun pingHttp(urlStr: String = "http://cp.cloudflare.com/generate_204", timeoutMs: Int = 3000): PingResult = withContext(Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        try {
            val startNano = System.nanoTime()
            val url = java.net.URL(urlStr)
            connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SOCKS5Proxy/1.5")
                setRequestProperty("Connection", "close")
            }
            connection.connect()
            val code = connection.responseCode
            val latencyMs = (System.nanoTime() - startNano) / 1_000_000L
            if (code in 200..399) {
                PingResult(true, latencyMs, "$latencyMs мс")
            } else {
                PingResult(false, latencyMs, "HTTP $code")
            }
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: java.net.SocketTimeoutException) {
            PingResult(false, 0, "Таймаут")
        } catch (e: Exception) {
            PingResult(false, 0, e.message ?: "Сбой соединения")
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun checkPublicIp(
        localSocksPort: Int? = null,
        timeoutMs: Int = 5000
    ): PublicIpResult = withContext(Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        try {
            val url = java.net.URL("https://ipwho.is/")
            val proxy = if (localSocksPort != null && localSocksPort in 1..65535) {
                java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", localSocksPort))
            } else {
                java.net.Proxy.NO_PROXY
            }
            connection = (url.openConnection(proxy) as java.net.HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Socks5ProxyAndroid")
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode !in 200..299) {
                return@withContext PublicIpResult(false, message = "HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(body)
            val ip = json.optString("ip").trim()
            val country = json.optString("country").trim()
            val countryCode = json.optString("country_code").trim()
            if (ip.isBlank()) {
                PublicIpResult(false, message = "Не удалось определить IP")
            } else {
                val label = listOfNotNull(countryCode.takeIf { it.isNotBlank() }, country.takeIf { it.isNotBlank() }).joinToString(" · ")
                PublicIpResult(true, ip = ip, country = label, message = if (label.isNotBlank()) "$ip ($label)" else ip)
            }
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (e: Exception) {
            PublicIpResult(false, message = e.message ?: "Ошибка проверки IP")
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun InputStream.readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = read(buffer, offset, buffer.size - offset)
            if (count < 0) throw java.io.EOFException("Неполный ответ SOCKS5")
            offset += count
        }
    }

    private fun InputStream.readHttpLine(maxLength: Int): String {
        val line = StringBuilder()
        while (line.length < maxLength) {
            val value = read()
            if (value < 0) break
            if (value == '\n'.code) break
            if (value != '\r'.code) line.append(value.toChar())
        }
        return line.toString()
    }
}
