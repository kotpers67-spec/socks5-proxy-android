package com.socks5proxy.app

import com.socks5proxy.app.model.ProxyProfile
import com.socks5proxy.app.network.Socks5Checker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.net.ServerSocket

class Socks5CheckerTest {

    @Test
    fun testPingTcpAgainstRealLocalSocket() = runBlocking {
        ServerSocket(0).use { server ->
            val acceptJob = launch(Dispatchers.IO) {
                repeat(3) {
                    runCatching { server.accept().close() }
                }
            }
            val result = Socks5Checker.pingTcp("127.0.0.1", server.localPort, timeoutMs = 1500)
            acceptJob.join()
            assertTrue(result.isSuccess)
            assertTrue(result.latencyMs >= 0)
        }
    }

    @Test
    fun testCheckConnectionWithRealSocks5Handshake() = runBlocking {
        ServerSocket(0).use { server ->
            val serverJob = launch(Dispatchers.IO) {
                val client = server.accept()
                client.use { s ->
                    val input = DataInputStream(s.getInputStream())
                    val output = s.getOutputStream()

                    // Greeting: VER=5, NMETHODS=2, METHODS=[0, 2]
                    val ver = input.readUnsignedByte()
                    val nMethods = input.readUnsignedByte()
                    val methods = ByteArray(nMethods)
                    input.readFully(methods)
                    assertEquals(5, ver)

                    // Select username/password auth (0x02)
                    output.write(byteArrayOf(0x05, 0x02))
                    output.flush()

                    // Read auth subnegotiation
                    assertEquals(0x01, input.readUnsignedByte())
                    val uLen = input.readUnsignedByte()
                    val uBytes = ByteArray(uLen)
                    input.readFully(uBytes)
                    val pLen = input.readUnsignedByte()
                    val pBytes = ByteArray(pLen)
                    input.readFully(pBytes)
                    assertEquals("u1", String(uBytes))
                    assertEquals("p1", String(pBytes))

                    output.write(byteArrayOf(0x01, 0x00))
                    output.flush()

                    // Read CONNECT request
                    val hdr = ByteArray(4)
                    input.readFully(hdr)
                    assertEquals(0x05, hdr[0].toInt() and 0xFF)
                    assertEquals(0x01, hdr[1].toInt() and 0xFF)
                    assertEquals(0x03, hdr[3].toInt() and 0xFF)
                    val hostLen = input.readUnsignedByte()
                    input.readFully(ByteArray(hostLen + 2))

                    // Reply success (0x00)
                    output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0, 80))
                    output.flush()

                    // Read HTTP GET headers until blank line before replying
                    val headerSb = StringBuilder()
                    while (!headerSb.endsWith("\r\n\r\n")) {
                        val b = input.read()
                        if (b < 0) break
                        headerSb.append(b.toChar())
                    }
                    output.write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".toByteArray())
                    output.flush()
                    s.shutdownOutput()
                    input.read()
                }
            }

            val profile = ProxyProfile(
                name = "LocalMockSocks",
                host = "127.0.0.1",
                port = server.localPort,
                username = "u1",
                password = "p1"
            )
            val res = Socks5Checker.checkConnection(profile, timeoutMs = 3000)
            serverJob.join()
            assertTrue(res.message, res.isSuccess)
        }
    }

    @Test
    fun testPingTcpInvalidInput() = runBlocking {
        val res = Socks5Checker.pingTcp("", 1080)
        assertFalse(res.isSuccess)
    }
}
