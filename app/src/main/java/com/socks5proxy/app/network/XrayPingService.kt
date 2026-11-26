package com.socks5proxy.app.network

import com.socks5proxy.app.model.VpnServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject

/**
 * Реальный замер задержки (Real Delay) VPN-серверов через встроенное ядро Xray (pingBatch).
 * Выполняет полноценное подключение по протоколу узла (VLESS/VMess/Trojan/SS/Hysteria2)
 * и замеряет HTTPS 204 ответ, а не только TCP SYN.
 */
object XrayPingService {
    private const val TEST_URL = "https://cp.cloudflare.com/generate_204"
    private const val TIMEOUT_SECONDS = 6

    suspend fun pingServers(servers: List<VpnServerProfile>): Map<String, PingResult> = withContext(Dispatchers.IO) {
        if (servers.isEmpty()) return@withContext emptyMap()

        val configs = JSONArray()
        for (server in servers) {
            val outbound = runCatching {
                JSONObject(server.outboundJson).put("tag", "vpn-node")
            }.getOrNull()
            if (outbound == null) continue

            val xrayConfig = JSONObject()
                .put("log", JSONObject().put("loglevel", "none"))
                .put("outbounds", JSONArray().put(outbound))

            configs.put(
                JSONObject()
                    .put("xrayJson", xrayConfig.toString())
                    .put("outboundTag", "vpn-node")
            )
        }

        if (configs.length() == 0) {
            return@withContext servers.associate { it.id to PingResult(false, 0, "Некорректный конфиг") }
        }

        val payload = JSONObject()
            .put("timeout", TIMEOUT_SECONDS)
            .put("url", TEST_URL)
            .put("configs", configs)

        val request = JSONObject()
            .put("apiVersion", 3)
            .put("method", "pingBatch")
            .put("payload", payload)

        val response = try {
            JSONObject(LibXray.invoke(request.toString()))
        } catch (e: Throwable) {
            return@withContext servers.associate {
                it.id to PingResult(false, 0, e.localizedMessage ?: "Ошибка Xray")
            }
        }

        if (!response.optBoolean("success")) {
            val err = response.optString("error").ifBlank { "Ошибка замера Xray" }
            return@withContext servers.associate { it.id to PingResult(false, 0, err) }
        }

        val resultsArray = response.optJSONObject("data")?.optJSONArray("results")
            ?: return@withContext servers.associate { it.id to PingResult(false, 0, "Пустой ответ Xray") }

        val mapped = LinkedHashMap<String, PingResult>(servers.size)
        for (i in servers.indices) {
            val server = servers[i]
            val item = resultsArray.optJSONObject(i)
            if (item == null) {
                mapped[server.id] = PingResult(false, 0, "Нет ответа")
                continue
            }
            val ok = item.optBoolean("success", false)
            val delay = item.optLong("delay", 0L)
            val err = item.optString("error", "").trim()
            if (ok && delay > 0L) {
                mapped[server.id] = PingResult(true, delay, "$delay мс")
            } else {
                mapped[server.id] = PingResult(false, 0L, err.ifBlank { "Недоступен" })
            }
        }
        mapped
    }
}
