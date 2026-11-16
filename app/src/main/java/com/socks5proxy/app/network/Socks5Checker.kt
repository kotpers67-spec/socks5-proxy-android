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

object Socks5Checker {
    private const val SOCKS_VERSION_5 = 0x05
    private const val METHOD_NO_AUTH = 0x00
    private const val METHOD_USER_PASS = 0x02
    private const val METHOD_NO_ACCEPTABLE = 0xFF
    private const val AUTH_SUBNEGOTIATION_VER = 0x01
    private const val AUTH_STATUS_SUCCESS = 0x00

    suspend fun checkConnection(profile: ProxyProfile, timeoutMs: Int = 4000): PingResult = withContext(Dispatchers.IO) {
        val startNano = System.nanoTime()
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
                if (authStatus != AUTH_STATUS_SUCCESS) {
                    return@withContext PingResult(false, 0, "Неверный логин или пароль SOCKS5")
                }
            }

            val latencyMs = (System.nanoTime() - startNano) / 1_000_000L
            PingResult(true, latencyMs, "SOCKS5 доступен (${latencyMs} мс)")

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
}
